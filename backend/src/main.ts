import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // Configuration CORS (autorise l'app mobile ou web)
  app.enableCors();

  // Validation stricte des DTOs
  app.useGlobalPipes(new ValidationPipe({
    whitelist: true, // Supprime les propriétés non déclarées dans le DTO
    forbidNonWhitelisted: true, // Rejette la requête si des propriétés non déclarées sont présentes
    transform: true, // Transforme automatiquement les payloads en instances de DTO
  }));

  // Configuration Swagger (Documentation OpenAPI)
  const config = new DocumentBuilder()
    .setTitle('SWIMVPN+ API')
    .setDescription('The core backend API for the SWIMVPN+ Android application')
    .setVersion('1.0')
    .build();

  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('api/docs', app, document);

  const port = process.env.PORT || 3000;
  await app.listen(port);
  console.log(`Application is running on: http://localhost:${port}`);
  console.log(`Swagger documentation available at: http://localhost:${port}/api/docs`);
}
bootstrap();
