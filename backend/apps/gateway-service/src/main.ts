import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { AppModule } from './app.module';
import { createGatewayRateLimitMiddleware } from './security/gateway-rate-limit';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { rawBody: true });

  // Global Prefix
  app.setGlobalPrefix('api/v1');

  // Validation
  app.useGlobalPipes(new ValidationPipe({
    whitelist: true,
    transform: true,
  }));

  app.use(createGatewayRateLimitMiddleware());

  const swaggerEnabled = process.env.NODE_ENV !== 'production' || process.env.GATEWAY_SWAGGER_ENABLED === 'true';
  if (swaggerEnabled) {
    // Swagger Documentation
    const config = new DocumentBuilder()
      .setTitle('SWIMVPN+ API Gateway')
      .setDescription('The core API gateway for SWIMVPN microservices')
      .setVersion('1.0')
      .addBearerAuth()
      .build();
    const document = SwaggerModule.createDocument(app, config);
    SwaggerModule.setup('docs', app, document);
  }

  const corsOrigins = (process.env.GATEWAY_CORS_ORIGINS || '')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);

  // Enable CORS for frontend. Empty allowlist keeps legacy behavior until production env is configured.
  app.enableCors(corsOrigins.length > 0 ? { origin: corsOrigins, credentials: false } : undefined);

  await app.listen(3000);
  console.log('SWIMVPN+ Gateway is running on http://localhost:3000/api/v1');
  if (swaggerEnabled) {
    console.log('Swagger documentation available at http://localhost:3000/docs');
  }
}
bootstrap();
