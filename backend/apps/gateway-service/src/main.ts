import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // Global Prefix
  app.setGlobalPrefix('api/v1');

  // Validation
  app.useGlobalPipes(new ValidationPipe({
    whitelist: true,
    transform: true,
  }));

  // Swagger Documentation
  const config = new DocumentBuilder()
    .setTitle('SWIMVPN+ API Gateway')
    .setDescription('The core API gateway for SWIMVPN microservices')
    .setVersion('1.0')
    .addBearerAuth()
    .build();
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('docs', app, document);

  // Enable CORS for frontend
  app.enableCors();

  await app.listen(3000);
  console.log('SWIMVPN+ Gateway is running on http://localhost:3000/api/v1');
  console.log('Swagger documentation available at http://localhost:3000/docs');
}
bootstrap();
