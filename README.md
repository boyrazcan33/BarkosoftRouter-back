# Route Optimization API

Free alternative to Google Maps Route Optimization API .

## Live Demo

🌐 **Frontend:** [http://barkosoft-router.surge.sh](http://barkosoft-router.surge.sh)

## Why This Project?

- **100% Free**: Google Route Optimization API costs money, this is completely free
- **High Performance**: Handles 500+ customers in ~50 seconds
- **Scalable**: Kafka-based parallel processing for large datasets
- **Production Ready**: OSRM + Spring Boot + React stack

## Performance vs Google API

| Service | 500 Customers | Cost | Features |
|---------|---------------|------|----------|
| **This API** | ~50 seconds | FREE | Unlimited requests |
| Google API | ~10-30 seconds | $$ | Limited free tier |

## Technical Architecture

### Scalability with Kafka
- **Small datasets** (≤50): Direct OSRM processing
- **Large datasets** (500+): Kafka batch processing (95 customers per batch)
- **Parallel processing**: 4 concurrent consumers
- **Fault tolerance**: Retry mechanism with dead letter queue

### Performance Optimizations
- **Connection pooling**: WebClient with connection reuse
- **Timeout handling**: 60-second API timeouts
- **Memory efficient**: Streaming batch results
- **CDN delivery**: Global deployment via Surge.sh

## Quick Start

```bash
npm install
npm start
```

## JSON Format

```json
{
  "startLatitude": 41.0082,
  "startLongitude": 28.9784,  
  "customers": [
    {"myId": 101, "latitude": 41.0180, "longitude": 28.9647}
  ]
}
```

## Tech Stack

- **Frontend**: React 19 + TypeScript + Leaflet
- **Backend**: Spring Boot + Kafka + OSRM
- **Deployment**: Surge.sh + Google Cloud

## Real-World Usage

Perfect for:
- Delivery route planning
- Field service optimization
- Logistics companies
- Any business needing route optimization without Google's pricing