import { Controller, Get, Param, Query } from '@nestjs/common';
import { ContentService } from './content.service';

@Controller('api/v1/content')
export class ContentController {
  constructor(private readonly contentService: ContentService) {}

  @Get(':type')
  async getContent(
    @Param('type') type: string,
    @Query('locale') locale: string
  ) {
    return this.contentService.getContent(type, locale);
  }
}
