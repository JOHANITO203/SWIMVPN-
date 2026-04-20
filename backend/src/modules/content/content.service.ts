import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class ContentService {
  constructor(private prisma: PrismaService) {}

  async getContent(type: string, locale: string = 'ru') {
    // On s'assure que la locale est supportée, sinon on fallback sur 'en' ou 'ru'
    const validLocales = ['ru', 'en', 'fr'];
    const safeLocale = validLocales.includes(locale) ? locale : 'en';

    const contents = await this.prisma.appContent.findMany({
      where: {
        type: type, // ex: 'onboarding', 'faq', 'offers'
        locale: safeLocale,
        isActive: true
      },
      select: {
        key: true,
        value: true
      }
    });

    // On transforme le tableau [{key: 'title', value: 'Hello'}] en objet {title: 'Hello'}
    // pour faciliter la consommation côté Android
    return contents.reduce((acc, curr) => {
      acc[curr.key] = curr.value;
      return acc;
    }, {});
  }
}
