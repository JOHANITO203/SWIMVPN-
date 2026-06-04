import { Controller, Get, Req } from '@nestjs/common';
import { Request } from 'express';
import * as geoip from 'geoip-lite';

/**
 * Public, unauthenticated echo endpoint.
 *
 * The Android "works-here" residential-proxy probe calls this THROUGH the pasted proxy over HTTPS,
 * so the device's only network dependency is api.swimvpn.pro itself — no third party, and the exit
 * IP is never leaked to an external service over plaintext (the previous probe hit ip-api.com over
 * HTTP). The backend sees the proxy's exit IP as the caller and resolves the country locally via
 * geoip-lite (bundled DB, no external call). Returns `{ ip, country }` (country = ISO-2 or null).
 */
@Controller()
export class StatusController {
  @Get('status/caller-ip')
  callerIp(@Req() req: Request): { ip: string; country: string | null } {
    const forwarded = (req.headers['x-forwarded-for'] as string | undefined)?.split(',')[0]?.trim();
    const realIp = (req.headers['x-real-ip'] as string | undefined)?.trim();
    const raw = (forwarded || realIp || req.ip || req.socket?.remoteAddress || '').trim();
    const ip = raw.replace(/^::ffff:/, ''); // unwrap IPv4-mapped IPv6 for geoip
    const country = ip ? geoip.lookup(ip)?.country ?? null : null;
    return { ip, country };
  }
}
