import {describe, expect, it, vi} from 'vitest';
import {AuthService} from '../service/auth.service';
import {API_CONFIG} from '../../core/config/api-config';
import {createRxStompConfig} from './rx-stomp.config';

describe('createRxStompConfig', () => {
  it('should build a config with the configured broker URL and defaults', () => {
    const authService = {getInternalAccessToken: vi.fn()} as unknown as AuthService;
    const config = createRxStompConfig(authService);

    expect(config.brokerURL).toBe(API_CONFIG.BROKER_URL);
    expect(config.heartbeatIncoming).toBe(0);
    expect(config.heartbeatOutgoing).toBe(20000);
    expect(config.reconnectDelay).toBe(10000);
    expect(typeof config.beforeConnect).toBe('function');
    expect(typeof config.debug).toBe('function');
  });

  it('should set Authorization header when a token is available', () => {
    const authService = {getInternalAccessToken: vi.fn(() => 'secret-token')} as unknown as AuthService;
    const config = createRxStompConfig(authService);

    const stompMock = {stompClient: {connectHeaders: {}}} as any;
    config.beforeConnect!(stompMock);

    expect(authService.getInternalAccessToken).toHaveBeenCalled();
    expect(stompMock.stompClient.connectHeaders).toEqual({
      Authorization: 'Bearer secret-token'
    });
  });

  it('should leave connectHeaders empty when no token is returned', () => {
    const authService = {getInternalAccessToken: vi.fn(() => null)} as unknown as AuthService;
    const config = createRxStompConfig(authService);

    const stompMock = {stompClient: {connectHeaders: {}}} as any;
    config.beforeConnect!(stompMock);

    expect(stompMock.stompClient.connectHeaders).toEqual({});
  });

  it('should handle an undefined token gracefully', () => {
    const authService = {getInternalAccessToken: vi.fn(() => undefined)} as unknown as AuthService;
    const config = createRxStompConfig(authService);

    const stompMock = {stompClient: {connectHeaders: {existing: 'header'}}} as any;
    config.beforeConnect!(stompMock);

    expect(stompMock.stompClient.connectHeaders).toEqual({existing: 'header'});
  });

  it('debug should be callable without throwing', () => {
    const authService = {getInternalAccessToken: vi.fn()} as unknown as AuthService;
    const config = createRxStompConfig(authService);

    expect(() => config.debug!('test message')).not.toThrow();
  });
});
