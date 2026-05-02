# Architecture Map

## Vue systeme

- [[../Repository Markdown/docs/ARCHITECTURE|Architecture]]
- [[../Repository Markdown/BACKEND_ARCHITECTURE_AND_ROADMAP|Backend Architecture and Roadmap]]
- [[../Repository Markdown/docs/DOMAIN_MODEL|Domain Model]]
- [[../Repository Markdown/docs/SOURCE_OF_TRUTH|Source of Truth]]

## Backend microservices

- gateway-service
- customer-order-service
- inventory-delivery-service
- admin-control-service
- vpn-config-engine-service
- store-engine-service
- notification-bot-service

## Principes critiques

- PostgreSQL et Prisma restent la source de verite.
- Le backend applique les droits premium, trial et freemium.
- Android ne doit pas inventer un etat d'acces independamment du backend.
- Les configs VPN brutes doivent rester intactes.

## Lire ensuite

- [[../Repository Markdown/docs/IMPLEMENTATION_RULES|Implementation Rules]]
- [[../Repository Markdown/AGENTS|AGENTS]]
- [[Operations Map]]
