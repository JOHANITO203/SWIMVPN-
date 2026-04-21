import { Controller, Post, Get, Body, Inject, UseGuards, Req } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { AdminGuard } from '../admin.guard';

@Controller('admin')
export class AdminController {
  constructor(@Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy) {}

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
}
