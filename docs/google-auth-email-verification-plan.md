# Implementation Plan: Optional Google Sign-In + Email Verification

> Self-contained plan. Written 2026-07-07. Execute top to bottom; all file paths are exact.
> Backend repo: `/Users/belalhossain/Documents/projects/oetexam` (Spring Boot 3.2.0, Java 21, base package `com.oet`, MySQL, jjwt 0.12.3, Flyway V1–V4).
> Frontend repo: `/Users/belalhossain/Documents/projects/oetexam-frontend` (Angular 21, standalone components, signals, zoneless, Tailwind, ngx-sonner toasts; file names have no `.component.ts` suffix).

## Goals

1. **Optional "Sign in with Google"** on login and register pages. Google Identity Services (GIS) button → Angular receives a Google ID token → `POST /api/auth/google` → backend verifies the token, finds-or-creates the user, returns the app's **existing** JWT access + refresh token pair. Email/password registration and login keep working unchanged as the primary path.
2. **Email verification for password registration**: SMTP via `spring-boot-starter-mail`. Login is **blocked (403)** until verified, with a resend option. **Existing users are grandfathered** as verified. Google users are automatically verified.
3. **Auto-linking**: a Google sign-in whose email matches an existing account logs into that account (Google verifies email ownership) and links `google_sub` to it.
4. New Google users are created with role `APPLICANT`.

## Relevant existing code (verified)

- `com/oet/auth/service/AuthService.java` — `register`, `login`, `refresh`; **private** `buildAuthResponse(User)` at line 86 mints access JWT + DB-backed opaque refresh token (UUID, one active per user, table `refresh_tokens`) and returns `AuthResponse(accessToken, refreshToken, tokenType, expiresIn, role, email, firstName, lastName)`. Reuse it for the new flows (widen to package-private).
- `com/oet/auth/controller/AuthController.java` — `/api/auth/**` endpoints, wraps everything in `ApiResponse<T>` (`com/oet/common/util/ApiResponse.java`: timestamp/status/data/error, factories `success`, `created`, `error`).
- `com/oet/common/exception/GlobalExceptionHandler.java` — maps `BusinessException`→409, `NotFoundException`→404, `BadCredentialsException`→401, `DisabledException`→403.
- `com/oet/user/entity/User.java` — `passwordHash` is currently `nullable = false`; no provider/googleSub/emailVerified fields. `UserRole` enum = `{ADMIN, APPLICANT}`.
- `com/oet/user/service/UserDetailsServiceImpl.java:29` — `.password(user.getPasswordHash())`: **crashes on null password** (Spring's `User.builder()` rejects null) and is called by `buildAuthResponse` on every login. Must be patched (step B7).
- `com/oet/config/SecurityConfig.java` — `/api/auth/**` already permitAll; **no change needed**. CORS from `cors.allowed-origins` property.
- Config: `app.jwt.*` in `application.yml` with env overrides. **Dev profile disables Flyway and uses `ddl-auto: update`** (Hibernate drives dev schema); base yml has Flyway enabled + `ddl-auto: validate`.
- Frontend `src/app/auth/services/auth.ts` (class `Auth`) — localStorage keys `access_token`/`refresh_token`/`current_user`; private `handleAuthSuccess()` centralizes storage + role redirect (`ADMIN`→`/admin/dashboard`, else `/student/home`).
- Frontend `src/app/auth/interceptors/auth-interceptor.ts` — `isAuthEndpoint()` whitelist at ~lines 118–124 currently lists `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`.
- `login.html` ~lines 94–109 and `register.html` ~lines 144–153 contain **commented-out** Google/Facebook button blocks — the insertion points.
- Frontend `tsconfig.app.json` has `"types": []`; environments (`src/environments/environment.ts`, `environment.development.ts`) only have `API_URL` (both currently point at prod `https://oet.belalhossain.dev/api`).
- Backend has **no tests yet**; no OAuth or mail dependencies in `pom.xml`.

## Prerequisites (manual)

1. **Google Cloud Console**: OAuth 2.0 **Web application** client ID (consent screen External, default openid/email/profile scopes). Authorized JavaScript origins: `http://localhost:4200` + prod frontend origin. No redirect URIs (GIS credential flow). The **same client ID** is used in Angular environments and backend `GOOGLE_CLIENT_ID` (token audience).
2. **SMTP credentials** (e.g. Gmail SMTP app password) via env vars: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM`.

## Part A — Design decisions (follow these; don't re-litigate)

- **Google token verification**: `spring-security-oauth2-jose` (Nimbus `JwtDecoder` against Google JWKS `https://www.googleapis.com/oauth2/v3/certs`) — BOM-managed, no heavy Google SDK deps, built-in JWKS caching. Validate: RS256 signature, `exp`/`nbf` (`JwtTimestampValidator`), `iss` ∈ {`https://accounts.google.com`, `accounts.google.com`}, `aud` contains our client ID. The service additionally requires claim `email_verified == true`.
- **Schema**: `users` gains `auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL'`, `google_sub VARCHAR(255) NULL UNIQUE`, `email_verified BOOLEAN NOT NULL DEFAULT FALSE`; `password_hash` becomes nullable. New table `email_verification_tokens` mirrors `refresh_tokens` (UUID token, 24h expiry, one active per user). Lookup by `google_sub` wins over email (Google `sub` is stable; emails can change).
- **Register API contract change**: `POST /api/auth/register` no longer returns tokens (no auto-login) — returns 201 with a "verification email sent" message.
- **Verify-email returns tokens**: successful verification auto-logs the user in (token proves inbox ownership).
- **Resend endpoint never reveals** whether an email exists (generic 200 always).
- **GIS on frontend**: load the official script dynamically (no extra npm auth library — Angular 21 compat risk with `@abacritt/angularx-social-login`).

## Part B — Backend steps

**B1. `pom.xml`**: add (no versions — Boot BOM):
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

**B2. `src/main/resources/application.yml`** — add:
```yaml
spring:
  mail:
    host: ${SMTP_HOST:smtp.gmail.com}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

app:
  google:
    client-id: ${GOOGLE_CLIENT_ID:}
  mail:
    from: ${MAIL_FROM:no-reply@oetpractice.com}
    verification-token-expiration-hours: 24
  frontend:
    base-url: ${FRONTEND_BASE_URL:http://localhost:4200}
```
Mirror dev-friendly defaults in `application-dev.yml`.

**B3. NEW `src/main/java/com/oet/config/GoogleTokenConfig.java`**:
```java
@Configuration
public class GoogleTokenConfig {
    @Bean(name = "googleIdTokenDecoder")
    public JwtDecoder googleIdTokenDecoder(@Value("${app.google.client-id}") String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null
                        && ("https://accounts.google.com".equals(iss.toString())
                            || "accounts.google.com".equals(iss.toString()))),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(clientId))));
        return decoder;
    }
}
```

**B4. NEW `src/main/java/com/oet/user/entity/AuthProvider.java`**: `public enum AuthProvider { LOCAL, GOOGLE }`

**B5. `src/main/java/com/oet/user/entity/User.java`**:
- `passwordHash`: drop `nullable = false` → `@Column(name = "password_hash")`.
- Add:
```java
@Enumerated(EnumType.STRING)
@Column(name = "auth_provider", nullable = false, length = 20)
@Builder.Default
private AuthProvider authProvider = AuthProvider.LOCAL;

@Column(name = "google_sub", unique = true)
private String googleSub;

@Column(name = "email_verified", nullable = false)
@Builder.Default
private boolean emailVerified = false;
```

**B6. `src/main/java/com/oet/user/repository/UserRepository.java`**: add `Optional<User> findByGoogleSub(String googleSub);`

**B7. `src/main/java/com/oet/user/service/UserDetailsServiceImpl.java:29`** (required fix): change `.password(user.getPasswordHash())` to `.password(user.getPasswordHash() != null ? user.getPasswordHash() : "")`. Bcrypt never matches `""`, so Google-only users cannot password-login (intended).

**B8. NEW entity + repository** (mirror `com/oet/auth/entity/RefreshToken.java` style):
- `com/oet/auth/entity/EmailVerificationToken.java`: `id`, `@ManyToOne(fetch = LAZY) User user`, `token` (unique, 512), `expiresAt`, `createdAt`, helper `isExpired()`. Table `email_verification_tokens`.
- `com/oet/auth/repository/EmailVerificationTokenRepository.java`: `Optional<EmailVerificationToken> findByToken(String token);` and `void deleteByUserId(Long userId);` (`@Modifying` as in `RefreshTokenRepository`).

**B9. NEW DTO records in `com/oet/auth/dto/`**:
- `GoogleLoginRequest(@NotBlank String idToken)`
- `VerifyEmailRequest(@NotBlank String token)`
- `ResendVerificationRequest(@NotBlank @Email String email)`
- `RegisterResponse(String message, String email)`

**B10. NEW `src/main/java/com/oet/auth/service/EmailService.java`**: wraps `JavaMailSender`. `@Async sendVerificationEmail(User user, String token)` builds link `{app.frontend.base-url}/auth/verify-email?token={token}` and sends a simple message; log the link at DEBUG (dev testing without a mailbox). Add `@EnableAsync` (e.g. new `com/oet/config/AsyncConfig.java` or on the application class).

**B11. NEW `src/main/java/com/oet/common/exception/EmailNotVerifiedException.java`** (extends `RuntimeException`) + handler in `GlobalExceptionHandler` → **403** with the exact message `"Email not verified. Please check your inbox."` (frontend matches on this to show the resend banner).

**B12. `src/main/java/com/oet/auth/service/AuthService.java`**:
- Change `buildAuthResponse` (line 86) from `private` to package-private (no modifier) — `GoogleAuthService` reuses it.
- `register()`: keep duplicate-email check and user creation; then delete prior verification tokens for the user, create a new `EmailVerificationToken` (UUID, expiry now + configured hours), call `emailService.sendVerificationEmail(...)`; return `RegisterResponse("Verification email sent", email)` — **no tokens**.
- `login()`: after `authenticationManager.authenticate(...)` and user load, if `!user.isEmailVerified()` throw `EmailNotVerifiedException`.
- NEW `@Transactional verifyEmail(VerifyEmailRequest)`: find token (`NotFoundException` if absent); if expired, delete it and throw `BusinessException("Verification link expired. Please request a new one.")`; else set `emailVerified = true`, delete token, return `buildAuthResponse(user)`.
- NEW `@Transactional resendVerification(ResendVerificationRequest)`: if email exists and is unverified → rotate token + resend; in all cases return a generic message (no enumeration).

**B13. NEW `src/main/java/com/oet/auth/service/GoogleAuthService.java`** (separate class keeps AuthService small):
```java
@Slf4j @Service @RequiredArgsConstructor
public class GoogleAuthService {
    private final @Qualifier("googleIdTokenDecoder") JwtDecoder googleIdTokenDecoder;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        Jwt jwt;
        try {
            jwt = googleIdTokenDecoder.decode(request.idToken());
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid Google token");
        }
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new BadCredentialsException("Google email is not verified");
        }
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email").toLowerCase();

        User user = userRepository.findByGoogleSub(sub)
                .or(() -> userRepository.findByEmail(email).map(existing -> {
                    existing.setGoogleSub(sub);          // auto-link
                    existing.setEmailVerified(true);     // Google verified it
                    return existing;                      // authProvider stays LOCAL — keeps password login
                }))
                .orElseGet(() -> createGoogleUser(jwt, sub, email));

        if (!user.isActive()) throw new DisabledException("Account is disabled");
        return authService.buildAuthResponse(user);
    }

    private User createGoogleUser(Jwt jwt, String sub, String email) {
        String firstName = firstNonBlank(jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("name"), email.substring(0, email.indexOf('@')));
        String lastName = Objects.requireNonNullElse(jwt.getClaimAsString("family_name"), "");
        return userRepository.save(User.builder()
                .email(email)
                .firstName(firstName).lastName(lastName)
                .role(UserRole.APPLICANT)
                .authProvider(AuthProvider.GOOGLE)
                .googleSub(sub)
                .emailVerified(true)
                .build());
    }
}
```
(Note: with Lombok `@RequiredArgsConstructor`, `@Qualifier` on fields needs `lombok.copyableAnnotations` config — simpler to write the constructor by hand with `@Qualifier` on the parameter.)

**B14. `src/main/java/com/oet/auth/controller/AuthController.java`**: inject `GoogleAuthService`; add:
- `POST /google` → `ApiResponse.success(googleAuthService.loginWithGoogle(request))`
- `POST /verify-email` → `ApiResponse.success(authService.verifyEmail(request))` (returns `AuthResponse`)
- `POST /resend-verification` → generic success message
- change `/register` to return `ApiResponse<RegisterResponse>` (still 201)
No `SecurityConfig` change (already permitAll under `/api/auth/**`).

**B15. NEW `src/main/resources/db/migration/V5__google_auth_and_email_verification.sql`** (MySQL):
```sql
ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(255) NULL;
ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN google_sub VARCHAR(255) NULL,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT uk_users_google_sub UNIQUE (google_sub);

UPDATE users SET email_verified = TRUE;   -- grandfather existing users

CREATE TABLE email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```
**Dev caveat**: the dev profile disables Flyway and uses `ddl-auto: update`. Hibernate will add the new columns/table, but will NOT relax NOT NULL on `password_hash` and will NOT run the grandfather UPDATE. Run once manually on the dev DB:
```sql
ALTER TABLE oet_practice.users MODIFY COLUMN password_hash VARCHAR(255) NULL;
UPDATE oet_practice.users SET email_verified = TRUE;
```

**B16. Tests** (Mockito, first tests in repo, `src/test/java/com/oet/auth/service/`; build Google `Jwt` objects with `Jwt.withTokenValue("t").header("alg","RS256").claim(...).build()`):
- `GoogleAuthServiceTest`: new user created (APPLICANT/GOOGLE/verified/null password); auto-link by email (provider stays LOCAL, becomes verified, googleSub set, no new user saved); lookup by sub short-circuits; `email_verified` false/absent → `BadCredentialsException`; decoder throws `JwtException` → `BadCredentialsException`; inactive user → `DisabledException`; name fallbacks (missing given/family name).
- `AuthServiceTest`: register creates token + sends email + returns no JWT; login unverified → `EmailNotVerifiedException`; verifyEmail valid/expired/unknown token; resendVerification generic 200 for unknown email.

## Part C — Frontend steps (`/Users/belalhossain/Documents/projects/oetexam-frontend`)

**C1. Typings**: `npm i -D @types/google.accounts`; in `tsconfig.app.json` set `"types": ["google.accounts"]`.

**C2. Environments**: add `googleClientId: '<client-id>.apps.googleusercontent.com'` to `src/environments/environment.ts` and `environment.development.ts`.

**C3. NEW `src/app/auth/services/google-identity.ts`** — GIS wrapper, dynamic script load (script loads only on auth pages):
```typescript
@Injectable({ providedIn: 'root' })
export class GoogleIdentity {
  private loaded?: Promise<void>;

  private load(): Promise<void> {
    this.loaded ??= new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'https://accounts.google.com/gsi/client';
      s.async = true; s.defer = true;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error('GIS load failed'));
      document.head.appendChild(s);
    });
    return this.loaded;
  }

  async renderButton(container: HTMLElement, onCredential: (idToken: string) => void) {
    await this.load();
    google.accounts.id.initialize({
      client_id: environment.googleClientId,
      callback: (resp) => onCredential(resp.credential),
    });
    google.accounts.id.renderButton(container, { theme: 'outline', size: 'large', width: 350 });
  }
}
```

**C4. `src/app/auth/services/auth.ts`** (class `Auth`):
- `loginWithGoogle(idToken: string)` → `POST ${API_URL}/auth/google` body `{ idToken }`, `tap(res => this.handleAuthSuccess(res))` (existing method — storage + role redirect).
- Change `register()` to expect `{ data: { message, email } }` — do NOT call `handleAuthSuccess`.
- `verifyEmail(token: string)` → `POST /auth/verify-email` body `{ token }`; response is a full `AuthResponse` → call `handleAuthSuccess` (auto-login after verification).
- `resendVerification(email: string)` → `POST /auth/resend-verification` body `{ email }`.

**C5. `src/app/auth/interceptors/auth-interceptor.ts`** (~lines 118–124): add `/api/auth/google`, `/api/auth/verify-email`, `/api/auth/resend-verification` to `isAuthEndpoint()`.

**C6. Login page** (`src/app/auth/pages/login/login.ts` + `login.html`):
- `login.ts`: implement `AfterViewInit`; inject `GoogleIdentity` and `NgZone`; `@ViewChild('googleBtn') googleBtn!: ElementRef<HTMLElement>`;
```typescript
ngAfterViewInit() {
  this.googleIdentity.renderButton(this.googleBtn.nativeElement, (idToken) =>
    this.zone.run(() => this.auth.loginWithGoogle(idToken).subscribe({
      error: (err) => toast.error(err?.error?.error || 'Google sign-in failed.'),
    }))
  ).catch(() => toast.error('Could not load Google Sign-In.'));
}
```
- `login.html` (~lines 94–109): replace the commented-out Google/Facebook block with an "Or login with" divider + `<div #googleBtn class="flex justify-center"></div>`. Drop the Facebook button.
- On login error 403 with message `"Email not verified..."`: show a signal-driven inline banner with a **Resend verification email** button calling `auth.resendVerification(email)`.

**C7. Register page** (`register.ts` + `register.html` ~lines 144–153): same GIS button pattern (`/auth/google` both signs in and auto-registers). On successful password registration, switch to a signal-controlled "Check your email" state (shows the submitted address + resend button) instead of redirecting.

**C8. NEW verify-email page** `src/app/auth/pages/verify-email/verify-email.ts` (+ `.html`), route `verify-email` added to the auth routes (guest area): read `token` query param on init, call `auth.verifyEmail(token)`; success → toast + redirect happens via `handleAuthSuccess`; failure (expired/invalid) → error state with resend option and link to login.

## Ordering

1. Backend: B1 → B2 → B3–B6 → B7 → B8–B11 → B12 → B13 → B14 → B15 → B16.
2. Manual: dev-DB ALTER/UPDATE statements; Google client ID; SMTP + `GOOGLE_CLIENT_ID` env vars.
3. Frontend: C1–C2 → C3 → C4 → C5 → C6 → C7 → C8.

## Verification (end-to-end, dev)

1. `./mvnw test` — all new tests pass.
2. Start backend with dev profile (`GOOGLE_CLIENT_ID` + SMTP env set); confirm Hibernate added the new columns/table; run the two manual dev-DB statements.
3. `environment.development.ts` currently points `API_URL` at prod — point it at the local backend. `ng serve` on port 4200 (must match the authorized JS origin).
4. **Registration + verification**: register via UI → "Check your email" state, no tokens in localStorage; login before verifying → 403 + resend banner; open emailed (or DEBUG-logged) link → verify-email page logs in and redirects to `/student/home`; DB shows `email_verified = 1`.
5. **Google new user**: sign in with an unused Gmail → redirect to `/student/home`; DB row: `role=APPLICANT`, `auth_provider=GOOGLE`, `google_sub` set, `password_hash` NULL, `email_verified` TRUE.
6. **Auto-link**: register + verify email X with a password, then Google-sign-in with X → same row gains `google_sub`, provider stays LOCAL, password login still works. Also link an unverified X → becomes verified.
7. **Negative**: garbage `idToken` via Swagger → 401 `ApiResponse`; expired/unknown verification token → clear error; `is_active=0` → 403; resend for unknown email → generic 200.
8. **Token lifecycle**: after Google login, expire the access token → interceptor single-flight refresh still works (refresh tokens are provider-agnostic).
9. Before prod deploy: Flyway V5 applies cleanly on a prod-schema copy (`ddl-auto: validate` fails fast on divergence); real SMTP delivers mail.
