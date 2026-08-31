package org.booklore.it.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FixtureFactory {

    private FixtureFactory() {
    }

    public static Path writePng(Path target) throws IOException {
        BufferedImage img = new BufferedImage(100, 150, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 100, 150);
        g.setColor(Color.WHITE);
        g.drawString("Cover", 30, 75);
        g.dispose();
        Files.createDirectories(target.getParent());
        ImageIO.write(img, "png", target.toFile());
        return target;
    }

    public static Path writePdf(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Hello from BookLore integration test");
                cs.endText();
            }
            doc.save(target.toFile());
        }
        return target;
    }

    public static Path writeCbz(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path png = Files.createTempFile("page", ".png");
        writePng(png);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            ZipEntry entry = new ZipEntry("page1.png");
            zos.putNextEntry(entry);
            Files.copy(png, zos);
            zos.closeEntry();
        }
        Files.deleteIfExists(png);
        return target;
    }

    public static Path writeEpub(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            byte[] mimetypeBytes = "application/epub+zip".getBytes(StandardCharsets.UTF_8);
            mimetype.setSize(mimetypeBytes.length);
            mimetype.setCompressedSize(mimetypeBytes.length);
            mimetype.setCrc(crc(mimetypeBytes));
            zos.putNextEntry(mimetype);
            zos.write(mimetypeBytes);
            zos.closeEntry();

            addZipEntry(zos, "META-INF/container.xml", containerXml());
            addZipEntry(zos, "OEBPS/content.opf", contentOpf());
            addZipEntry(zos, "OEBPS/chapter.xhtml", chapterXhtml());
        }
        return target;
    }

    public static Path writeAny(String extension, Path target) throws IOException {
        return switch (extension.toLowerCase()) {
            case "pdf" -> writePdf(target);
            case "png" -> writePng(target);
            case "cbz" -> writeCbz(target);
            case "epub" -> writeEpub(target);
            default -> throw new IllegalArgumentException("Unsupported fixture extension: " + extension);
        };
    }

    private static void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static long crc(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static String containerXml() {
        return "<?xml version=\"1.0\"?>\n" +
                "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
                "  <rootfiles>\n" +
                "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
                "  </rootfiles>\n" +
                "</container>";
    }

    private static String contentOpf() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"bookid\">\n" +
                "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                "    <dc:title>Integration Test Book</dc:title>\n" +
                "    <dc:language>en</dc:language>\n" +
                "  </metadata>\n" +
                "  <manifest>\n" +
                "    <item id=\"chapter1\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/>\n" +
                "  </manifest>\n" +
                "  <spine>\n" +
                "    <itemref idref=\"chapter1\"/>\n" +
                "  </spine>\n" +
                "</package>";
    }

    private static String chapterXhtml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "  <head><title>Test</title></head>\n" +
                "  <body><p>Hello, BookLore reader integration test.</p></body>\n" +
                "</html>";
    }
}
