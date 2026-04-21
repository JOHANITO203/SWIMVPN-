import { Module } from '@nestjs/common';
import { DatabaseModule } from '@app/database';
import { StoreController } from './store.controller';
import { StoreService } from './store.service';

@Module({
  imports: [DatabaseModule],
  controllers: [StoreController],
  providers: [StoreService],
})
export class AppModule {}