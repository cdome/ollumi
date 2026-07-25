import { RxStompService } from './rx-stomp.service';

export function rxStompServiceFactory(): RxStompService {
  return new RxStompService();
}
