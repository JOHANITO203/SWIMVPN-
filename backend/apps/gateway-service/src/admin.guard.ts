import {
  CanActivate,
  ExecutionContext,
  Injectable,
  Inject,
  UnauthorizedException,
} from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { firstValueFrom } from 'rxjs';

@Injectable()
export class AdminGuard implements CanActivate {
  constructor(@Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest();
    const authHeader = request.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      throw new UnauthorizedException('No token provided');
    }

    const token = authHeader.split(' ')[1];

    try {
      const admin = await firstValueFrom(
        this.adminClient.send({ cmd: 'validate_admin_token' }, { token }),
      );

      if (!admin) {
        throw new UnauthorizedException('Invalid or expired token');
      }

      request.admin = admin;
      return true;
    } catch (e) {
      throw new UnauthorizedException('Authentication failed');
    }
  }
}
