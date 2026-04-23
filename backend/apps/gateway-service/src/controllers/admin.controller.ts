import { Controller, Post, Get, Body, Inject, UseGuards, Req, Headers } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { AdminGuard } from '../admin.guard';

@Controller('admin')
export class AdminController {
  constructor(
    @Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy,
    @Inject('VPN_CONFIG_SERVICE') private readonly vpnConfigClient: ClientProxy,
  ) {}

  @Post('login')
  login(@Body() data: any) {
    return this.adminClient.send({ cmd: 'admin_login' }, data);
  }

  @UseGuards(AdminGuard)
  @Post('plans')
  createPlan(@Body() data: any, @Req() req: any) {
    return this.adminClient.send({ cmd: 'create_plan' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Get('plans')
  getPlans() {
    return this.adminClient.send({ cmd: 'get_plans' }, {});
  }

  @UseGuards(AdminGuard)
  @Post('import')
  importConfigs(@Body() data: any, @Req() req: any) {
    return this.adminClient.send({ cmd: 'trigger_import' }, { ...data, adminId: req.admin.id });
  }

  @UseGuards(AdminGuard)
  @Post('crypt-import')
  generateCryptImport(@Body() data: any) {
    return this.vpnConfigClient.send({ cmd: 'generate_swim_crypt_import' }, data);
  }

  @UseGuards(AdminGuard)
  @Post('logout')
  logout(@Headers('authorization') authorization: string) {
    const token = authorization?.startsWith('Bearer ') ? authorization.split(' ')[1] : '';
    return this.adminClient.send({ cmd: 'admin_logout' }, { token });
  }
}
