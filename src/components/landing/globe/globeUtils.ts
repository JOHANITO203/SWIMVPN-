import * as THREE from 'three';

export interface City {
  name: string;
  lat: number;
  lng: number;
}

export const CITIES: City[] = [
  { name: 'New York', lat: 40.7128, lng: -74.0060 },
  { name: 'London', lat: 51.5074, lng: -0.1278 },
  { name: 'Paris', lat: 48.8566, lng: 2.3522 },
  { name: 'Dubai', lat: 25.2048, lng: 55.2708 },
  { name: 'Singapore', lat: 1.3521, lng: 103.8198 },
  { name: 'Tokyo', lat: 35.6762, lng: 139.6503 },
  { name: 'Sydney', lat: -33.8688, lng: 151.2093 },
  { name: 'Moscow', lat: 55.7558, lng: 37.6173 },
  { name: 'São Paulo', lat: -23.5505, lng: -46.6333 },
  { name: 'San Francisco', lat: 37.7749, lng: -122.4194 },
  { name: 'Lagos', lat: 6.5244, lng: 3.3792 },
  { name: 'Frankfurt', lat: 50.1109, lng: 8.6821 },
  { name: 'Mumbai', lat: 19.0760, lng: 72.8777 },
];

export function latLonToVector3(lat: number, lng: number, radius: number): THREE.Vector3 {
  const phi = (90 - lat) * (Math.PI / 180);
  const theta = (lng + 180) * (Math.PI / 180);

  const x = -(radius * Math.sin(phi) * Math.cos(theta));
  const z = radius * Math.sin(phi) * Math.sin(theta);
  const y = radius * Math.cos(phi);

  return new THREE.Vector3(x, y, z);
}

export function createArcCurve(p1: THREE.Vector3, p2: THREE.Vector3, radius: number) {
  const midPoint = new THREE.Vector3().addVectors(p1, p2).multiplyScalar(0.5);
  const distance = p1.distanceTo(p2);
  const altitude = radius + distance * 0.25;
  
  midPoint.normalize().multiplyScalar(altitude);
  
  const curve = new THREE.QuadraticBezierCurve3(p1, midPoint, p2);
  return curve;
}
