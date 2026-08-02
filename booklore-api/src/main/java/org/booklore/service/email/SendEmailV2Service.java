package org.booklore.service.email;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.SendBookByEmailRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.repository.jooq.dto.EmailProviderV2Row;
import org.booklore.model.dto.EmailRecipientV2;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookRepository;
import org.booklore.repository.jooq.JooqEmailProviderV2Repository;
import org.booklore.repository.jooq.JooqEmailRecipientV2Repository;
import org.booklore.repository.jooq.JooqUserEmailProviderPreferenceRepository;
import org.booklore.repository.jooq.dto.UserEmailProviderPreference;
import org.booklore.service.NotificationService;
import org.booklore.util.FileUtils;
import org.booklore.util.SecurityContextVirtualThread;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

@Slf4j
@Service
@AllArgsConstructor
public class SendEmailV2Service {

    private final JooqEmailProviderV2Repository emailProviderRepository;
    private final JooqUserEmailProviderPreferenceRepository preferenceRepository;
    private final BookRepository bookRepository;
    private final JooqEmailRecipientV2Repository emailRecipientRepository;
    private final NotificationService notificationService;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public void emailBookQuick(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        EmailProviderV2Row defaultEmailProvider = getDefaultEmailProvider();
        EmailRecipientV2 defaultEmailRecipient = emailRecipientRepository.findDefaultEmailRecipientByUserId(user.getId());
        if (defaultEmailRecipient == null) {
            throw ApiError.DEFAULT_EMAIL_RECIPIENT_NOT_FOUND.createException();
        }
        BookFileEntity bookFile = book.getPrimaryBookFile();
        sendEmailInVirtualThread(defaultEmailProvider, defaultEmailRecipient.getEmail(), book, bookFile);
    }

    public void emailBook(SendBookByEmailRequest request) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        EmailProviderV2Row emailProvider = emailProviderRepository.findByIdAndUserId(request.getProviderId(), user.getId());
        if (emailProvider == null) {
            emailProvider = emailProviderRepository.findSharedProviderById(request.getProviderId());
        }
        if (emailProvider == null) {
            throw ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(request.getProviderId());
        }
        BookEntity book = bookRepository.findByIdWithBookFiles(request.getBookId()).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));
        EmailRecipientV2 emailRecipient = emailRecipientRepository.findByIdAndUserId(request.getRecipientId(), user.getId());
        if (emailRecipient == null) {
            throw ApiError.EMAIL_RECIPIENT_NOT_FOUND.createException(request.getRecipientId());
        }
        BookFileEntity bookFile = resolveBookFile(book, request.getBookFileId());
        sendEmailInVirtualThread(emailProvider, emailRecipient.getEmail(), book, bookFile);
    }

    private void sendEmailInVirtualThread(EmailProviderV2Row emailProvider, String recipientEmail, BookEntity book, BookFileEntity bookFile) {
        String bookTitle = book.getMetadata().getTitle();
        String logMessage = "Email dispatch initiated for book: " + bookTitle + " to " + recipientEmail;
        notificationService.sendMessage(Topic.LOG, LogNotification.info(logMessage));
        log.info(logMessage);
        SecurityContextVirtualThread.runWithSecurityContext(() -> {
            try {
                sendEmail(emailProvider, recipientEmail, book, bookFile);
                auditService.log(AuditAction.BOOK_SENT, "Book", book.getId(), "Sent book: " + bookTitle + " to " + recipientEmail);
                String successMessage = "The book: " + bookTitle + " has been successfully sent to " + recipientEmail;
                notificationService.sendMessage(Topic.LOG, LogNotification.info(successMessage));
                log.info(successMessage);
            } catch (Exception e) {
                String userMessage = "Failed to send book: " + bookTitle + " to " + recipientEmail + ". " + extractUserFriendlyMessage(e);
                notificationService.sendMessage(Topic.LOG, LogNotification.error(userMessage));
                log.error("Email send failed for book '{}' to {}: {}", bookTitle, recipientEmail, e.getMessage(), e);
            }
        });
    }

    private void sendEmail(EmailProviderV2Row emailProvider, String recipientEmail, BookEntity book, BookFileEntity bookFileEntity) throws MessagingException {
        JavaMailSenderImpl dynamicMailSender = setupMailSender(emailProvider);
        MimeMessage message = dynamicMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(StringUtils.firstNonEmpty(emailProvider.getFromAddress(), emailProvider.getUsername()));
        helper.setTo(recipientEmail);
        helper.setSubject("Your Book from Booklore: " + book.getMetadata().getTitle());
        helper.setText(generateEmailBody(book.getMetadata().getTitle()));
        File bookFile = new File(FileUtils.getBookFullPath(book, bookFileEntity));
        helper.addAttachment(bookFile.getName(), bookFile);
        dynamicMailSender.send(message);
        log.info("Book sent successfully to {}", recipientEmail);
    }

    private BookFileEntity resolveBookFile(BookEntity book, Long bookFileId) {
        if (bookFileId == null) {
            return book.getPrimaryBookFile();
        }
        return book.getBookFiles().stream()
                .filter(bf -> bf.getId().equals(bookFileId))
                .findFirst()
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException(bookFileId));
    }

    private JavaMailSenderImpl setupMailSender(EmailProviderV2Row emailProvider) {
        JavaMailSenderImpl dynamicMailSender = new JavaMailSenderImpl();
        dynamicMailSender.setHost(emailProvider.getHost());
        dynamicMailSender.setPort(emailProvider.getPort());
        dynamicMailSender.setUsername(emailProvider.getUsername());
        dynamicMailSender.setPassword(emailProvider.getPassword());

        Properties mailProps = dynamicMailSender.getJavaMailProperties();
        mailProps.put("mail.smtp.auth", emailProvider.getAuth());

        ConnectionType connectionType = determineConnectionType(emailProvider);
        configureConnectionType(mailProps, connectionType, emailProvider);
        configureTimeouts(mailProps);

        String debugMode = System.getProperty("mail.debug", "false");
        mailProps.put("mail.debug", debugMode);

        log.info("Email configuration: Host={}, Port={}, Type={}, Timeouts=60s", emailProvider.getHost(), emailProvider.getPort(), connectionType);

        return dynamicMailSender;
    }

    private ConnectionType determineConnectionType(EmailProviderV2Row emailProvider) {
        if (emailProvider.getPort() == 465) {
            return ConnectionType.SSL;
        } else if (emailProvider.getPort() == 587 && emailProvider.getStartTls()) {
            return ConnectionType.STARTTLS;
        } else if (emailProvider.getStartTls()) {
            return ConnectionType.STARTTLS;
        } else {
            return ConnectionType.PLAIN;
        }
    }

    private void configureConnectionType(Properties mailProps, ConnectionType connectionType, EmailProviderV2Row emailProvider) {
        switch (connectionType) {
            case SSL -> {
                mailProps.put("mail.transport.protocol", "smtps");
                mailProps.put("mail.smtp.ssl.enable", "true");
                mailProps.put("mail.smtp.ssl.trust", emailProvider.getHost());
                mailProps.put("mail.smtp.starttls.enable", "false");
                mailProps.put("mail.smtp.ssl.protocols", "TLSv1.2,TLSv1.3");
                mailProps.put("mail.smtp.ssl.checkserveridentity", "false");
                mailProps.put("mail.smtp.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                mailProps.put("mail.smtp.ssl.socketFactory.fallback", "false");
            }
            case STARTTLS -> {
                mailProps.put("mail.transport.protocol", "smtp");
                mailProps.put("mail.smtp.starttls.enable", "true");
                mailProps.put("mail.smtp.starttls.required", "true");
                mailProps.put("mail.smtp.ssl.enable", "false");
            }
            case PLAIN -> {
                mailProps.put("mail.transport.protocol", "smtp");
                mailProps.put("mail.smtp.starttls.enable", "false");
                mailProps.put("mail.smtp.ssl.enable", "false");
            }
        }
    }

    private void configureTimeouts(Properties mailProps) {
        String connectionTimeout = System.getProperty("mail.smtp.connectiontimeout", "60000");
        String socketTimeout = System.getProperty("mail.smtp.timeout", "60000");
        String writeTimeout = System.getProperty("mail.smtp.writetimeout", "60000");

        mailProps.put("mail.smtp.connectiontimeout", connectionTimeout);
        mailProps.put("mail.smtp.timeout", socketTimeout);
        mailProps.put("mail.smtp.writetimeout", writeTimeout);
    }

    private String generateEmailBody(String bookTitle) {
        return String.format("""
                Hello,
                
                You have received a book from Booklore. Please find the attached file titled '%s' for your reading pleasure.
                
                Thank you for using Booklore! Hope you enjoy your book.
                """, bookTitle);
    }

    private EmailProviderV2Row getDefaultEmailProvider() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        Long defaultProviderId = preferenceRepository.findByUserId(user.getId())
                .map(UserEmailProviderPreference::getDefaultProviderId)
                .orElseThrow(ApiError.DEFAULT_EMAIL_PROVIDER_NOT_FOUND::createException);

        EmailProviderV2Row provider = emailProviderRepository.findAccessibleProvider(defaultProviderId, user.getId());
        if (provider == null) {
            throw ApiError.DEFAULT_EMAIL_PROVIDER_NOT_FOUND.createException();
        }
        return provider;
    }

    private String extractUserFriendlyMessage(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof IOException) {
                return "The email provider rejected or dropped the connection during transfer. This often happens when the attachment exceeds the provider's size limit.";
            }
            cause = cause.getCause();
        }
        if (e instanceof MessagingException) {
            return "The email could not be sent due to a mail server error. Please verify your email provider settings.";
        }
        return "An unexpected error occurred: " + e.getMessage();
    }

    private enum ConnectionType {
        SSL,
        STARTTLS,
        PLAIN
    }
}