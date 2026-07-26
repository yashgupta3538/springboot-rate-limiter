# springboot-rate-limiter

A distributed API rate limiter built as a **Spring Cloud Gateway**, using the **Token Bucket algorithm** with shared state in **Redis**. Requests are throttled per client *before* they're proxied to the backend, and the bucket state lives in Redis so the limit holds true even if the gateway is scaled to multiple instances.

## How it works

```
Client
  → Spring Cloud Gateway (:8080)
      → route "api-route" matches /api/**
      → RateLimiterFilter checks the caller's bucket in Redis
          → ALLOWED → strips the /api prefix and proxies to the backend
          → BLOCKED → returns 429 with rate-limit headers, request never leaves the gateway
```

- **Client identification** — the caller is identified by the first IP in `X-Forwarded-For`, falling back to the socket's remote address.
- **Token bucket** — each client gets a bucket of `capacity` tokens that refills at `refill-rate` tokens/second. Every request consumes one token; refill is computed lazily from elapsed time on each call, so there's no background job ticking buckets up.
- **Atomicity via Lua** — the "read tokens → compute refill → allow/deny → write tokens" sequence runs as a single Lua script inside Redis, so concurrent requests from the same client can't race each other into over-admitting.
- **Response headers** — `X-RateLimit-Limit` and `X-RateLimit-Remaining` are added to both allowed and blocked responses.

## Project structure

```
src/main/java/com/rate/limiter/
├── RateLimitingApplication.java     # Spring Boot entrypoint
├── config/
│   ├── GatewayConfig.java           # defines the /api/** route + attaches the filter
│   ├── RateLimiterProperties.java   # capacity / refill-rate / backend URL (rate-limiter.*)
│   └── RedisProperties.java         # Jedis connection pool (spring.data.redis.*)
├── filter/
│   └── RateLimiterFilter.java       # GatewayFilterFactory — the actual interception point
├── service/
│   ├── RateLimiterService.java      # thin façade over the Redis-backed implementation
│   └── RedisTokenBucketService.java # token bucket logic, atomic Lua script
└── controller/
    └── StatusController.java        # /status/health and /status/rate-limit (not rate-limited)
```

## Requirements

- Java 17
- Maven (or the bundled `./mvnw`)
- Redis running locally (or reachable at the host/port configured below)

## Configuration

Set in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Gateway listen port |
| `rate-limiter.capacity` | `100` | Max tokens per client (burst size) |
| `rate-limiter.refill-rate` | `100` | Tokens added per second |
| `rate-limiter.api-server-url` | `http://localhost:8080` | Backend the gateway proxies `/api/**` to — **point this at your actual downstream service**, not at the gateway itself |
| `rate-limiter.timeout` | `5000` | Backend call timeout (ms) |
| `spring.data.redis.host` / `.port` | `localhost` / `6379` | Redis connection |
| `spring.data.redis.timeout` | `2000` | Redis connection timeout (ms) |

All values are overridable via environment variables or `--spring.config.location` at startup.

## Running locally

```bash
# start Redis
redis-server

# run the gateway
./mvnw spring-boot:run
```

Send a request through the gateway:

```bash
curl -i http://localhost:8080/api/your-endpoint
```

Check current bucket status for your client:

```bash
curl http://localhost:8080/status/rate-limit
```

Health check:

```bash
curl http://localhost:8080/status/health
```

## Example: hitting the limit

With the default `capacity=10`, `refill-rate=5`, the first 10 requests in quick succession succeed; the 11th gets:

```json
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0

{"error":"Rate limit Exceeded","clientId":"127.0.0.1"}
```

Tokens then refill continuously at 5/second until back at capacity.

## Load testing

Performance was validated with Apache JMeter against a running instance — tune `rate-limiter.capacity` / `rate-limiter.refill-rate` to the throughput you want to test against before running a load test.

## Known limitations / roadmap

- Client identification trusts `X-Forwarded-For` as-is; in production this should only be trusted from a known proxy/load balancer.
- Redis keys for token/refill state don't currently expire, so long-running deployments should add a TTL.
- Rate limiting is per-IP only; per-API-key or per-authenticated-user limiting would need `RateLimiterFilter`'s client-ID extraction swapped out.
- No automated test suite yet.

## Tech stack

Java 17 · Spring Boot 3 · Spring Cloud Gateway · Redis (Jedis) · Lombok · Apache JMeter (load testing)
