import * as THREE from 'three';

export type City = {
  name: string;
  lat: number;
  lng: number;
};

export const CITIES: City[] = [
  { name: 'TOKYO', lat: 35.6762, lng: 139.6503 },
  { name: 'SINGAPORE', lat: 1.3521, lng: 103.8198 },
  { name: 'DUBAI', lat: 25.2048, lng: 55.2708 },
  { name: 'LONDON', lat: 51.5072, lng: -0.1276 },
  { name: 'PARIS', lat: 48.8566, lng: 2.3522 },
  { name: 'NEW YORK', lat: 40.7128, lng: -74.006 },
  { name: 'SAN FRANCISCO', lat: 37.7749, lng: -122.4194 },
  { name: 'SÃO PAULO', lat: -23.5558, lng: -46.6396 },
  { name: 'SYDNEY', lat: -33.8688, lng: 151.2093 },
  { name: 'JOHANNESBURG', lat: -26.2041, lng: 28.0473 },
  { name: 'FRANKFURT', lat: 50.1109, lng: 8.6821 },
  { name: 'MUMBAI', lat: 19.076, lng: 72.8777 },
  { name: 'HONG KONG', lat: 22.3193, lng: 114.1694 },
  { name: 'SEOUL', lat: 37.5665, lng: 126.9780 },
  { name: 'TORONTO', lat: 43.6510, lng: -79.3470 },
  { name: 'AMSTERDAM', lat: 52.3676, lng: 4.9041 },
];

export function latLonToVector3(lat: number, lng: number, radius = 1): THREE.Vector3 {
  const phi = (90 - lat) * (Math.PI / 180);
  const theta = (lng + 180) * (Math.PI / 180);

  const x = -(radius * Math.sin(phi) * Math.cos(theta));
  const z = radius * Math.sin(phi) * Math.sin(theta);
  const y = radius * Math.cos(phi);

  return new THREE.Vector3(x, y, z);
}

export function createArcCurve(
  start: THREE.Vector3,
  end: THREE.Vector3,
  radius = 1,
): THREE.QuadraticBezierCurve3 {
  const mid = new THREE.Vector3().addVectors(start, end).multiplyScalar(0.5);
  const distance = start.distanceTo(end);

  const altitude = radius * (1.18 + Math.min(distance * 0.18, 0.42));
  mid.normalize().multiplyScalar(altitude);

  return new THREE.QuadraticBezierCurve3(start, mid, end);
}