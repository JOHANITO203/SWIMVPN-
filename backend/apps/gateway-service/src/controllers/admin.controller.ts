import { Controller, Post, Get, Body, Inject } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';

@Controller('admin')
export class AdminController {
  constructor(@Inject('ADMIN_SERVICE') private readonly adminClient: ClientProxy) {}

  @Post('login')
  login(@Body() data: any) {
    return this.adminClient.send({ cmd: 'admin_login' }, data);
  }

  @Post('plans')
  createPlan(@Body() data: any) {
    return this.adminClient.send({ cmd: 'create_plan' }, data);
  }

  @Get('plans')
  getPlans() {
    return this.adminClient.send({ cmd: 'get_plans' }, {});
  }

  @Post('import')
  importConfigs(@Body() data: any) {
    return this.adminClient.send({ cmd: 'trigger_import' }, data);
  }
}
