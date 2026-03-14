# SSDID SDK Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a standalone Rust SDK that lets any app request credentials from ssdid-wallet, authenticate users via DID, and issue credentials — via deeplink or QR code.

**Architecture:** Rust workspace with 3 crates: `ssdid-sdk-core` (pure logic), `ssdid-sdk-qr` (QR image generation), `ssdid-sdk-ffi` (UniFFI bindings for Kotlin/Swift). Tauri apps consume `ssdid-sdk-core` directly. The SDK generates DCQL-based authorization requests, encodes them as deeplinks or QR codes, and parses callback responses.

**Tech Stack:** Rust, UniFFI (proc-macro approach), serde/serde_json, url crate, qrcode + image crates, base64 crate

**Design doc:** `docs/plans/2026-03-14-ssdid-sdk-design.md`

---

## Task 1: Scaffold Rust Workspace

**Files:**
- Create: `/Users/amirrudinyahaya/Workspace/ssdid-sdk/Cargo.toml`
- Create: `/Users/amirrudinyahaya/Workspace/ssdid-sdk/crates/ssdid-sdk-core/Cargo.toml`
- Create: `/Users/amirrudinyahaya/Workspace/ssdid-sdk/crates/ssdid-sdk-core/src/lib.rs`
- Create: `/Users/amirrudinyahaya/Workspace/ssdid-sdk/crates/ssdid-sdk-qr/Cargo.toml`
- Create: `/Users/amirrudinyahaya/Workspace/ssdid-sdk/crates/ssdid-sdk-qr/src/lib.rs`
- Create: `/Users/amirrudinyahaya/Workspace/ssdid-sdk/crates/ssdid-sdk-ffi/Cargo.toml`
- Create: `/Users/amirrudinyahaya/Workspace/ssdid-sdk/crates/ssdid-sdk-ffi/src/lib.rs`
- Create: `/Users/amirrudinyahaya/Workspace/ssdid-sdk/.gitignore`

**Step 1: Create the repo and workspace Cargo.toml**

```bash
mkdir -p /Users/amirrudinyahaya/Workspace/ssdid-sdk
cd /Users/amirrudinyahaya/Workspace/ssdid-sdk
git init
```

```toml
# Cargo.toml (workspace root)
[workspace]
resolver = "2"
members = [
    "crates/ssdid-sdk-core",
    "crates/ssdid-sdk-qr",
    "crates/ssdid-sdk-ffi",
]

[workspace.package]
version = "0.1.0"
edition = "2021"
license = "MIT"
```

```gitignore
# .gitignore
/target
Cargo.lock
*.so
*.dylib
*.dll
```

**Step 2: Create ssdid-sdk-core crate**

```toml
# crates/ssdid-sdk-core/Cargo.toml
[package]
name = "ssdid-sdk-core"
version.workspace = true
edition.workspace = true

[dependencies]
serde = { version = "1", features = ["derive"] }
serde_json = "1"
url = "2"
base64 = "0.22"
uuid = { version = "1", features = ["v4"] }
```

```rust
// crates/ssdid-sdk-core/src/lib.rs
pub mod error;
pub mod dcql;
pub mod credential_request;
pub mod auth_request;
pub mod credential_offer;
pub mod delivery;
pub mod callback;
pub mod response_parser;
```

**Step 3: Create ssdid-sdk-qr crate**

```toml
# crates/ssdid-sdk-qr/Cargo.toml
[package]
name = "ssdid-sdk-qr"
version.workspace = true
edition.workspace = true

[dependencies]
ssdid-sdk-core = { path = "../ssdid-sdk-core" }
qrcode = "0.14"
image = { version = "0.25", default-features = false, features = ["png"] }
```

```rust
// crates/ssdid-sdk-qr/src/lib.rs
pub mod qr_generator;
```

**Step 4: Create ssdid-sdk-ffi crate**

```toml
# crates/ssdid-sdk-ffi/Cargo.toml
[package]
name = "ssdid-sdk-ffi"
version.workspace = true
edition.workspace = true

[lib]
crate-type = ["cdylib", "staticlib"]
name = "ssdid_sdk_ffi"

[dependencies]
ssdid-sdk-core = { path = "../ssdid-sdk-core" }
ssdid-sdk-qr = { path = "../ssdid-sdk-qr" }
uniffi = { version = "0.28" }

[build-dependencies]
uniffi = { version = "0.28", features = ["build"] }
```

```rust
// crates/ssdid-sdk-ffi/src/lib.rs
uniffi::setup_scaffolding!();
```

**Step 5: Verify workspace compiles**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-sdk && cargo build`
Expected: BUILD SUCCESS (with warnings about empty modules)

**Step 6: Commit**

```bash
git add -A
git commit -m "chore: scaffold ssdid-sdk Rust workspace with 3 crates"
```

---

## Task 2: Error Types

**Files:**
- Create: `crates/ssdid-sdk-core/src/error.rs`
- Test: inline `#[cfg(test)]`

**Step 1: Write the failing test**

```rust
// crates/ssdid-sdk-core/src/error.rs
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn error_display_invalid_config() {
        let err = SdkError::InvalidConfig("missing response_url".into());
        assert_eq!(err.to_string(), "Invalid configuration: missing response_url");
    }

    #[test]
    fn error_display_invalid_url() {
        let err = SdkError::InvalidUrl("http://insecure.com".into());
        assert_eq!(err.to_string(), "Invalid URL: http://insecure.com");
    }

    #[test]
    fn error_display_invalid_format() {
        let err = SdkError::InvalidFormat("unknown_format".into());
        assert_eq!(err.to_string(), "Invalid credential format: unknown_format");
    }

    #[test]
    fn error_display_response_parse() {
        let err = SdkError::ResponseParseError("malformed JSON".into());
        assert_eq!(err.to_string(), "Response parse error: malformed JSON");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-sdk && cargo test -p ssdid-sdk-core`
Expected: FAIL — `SdkError` not defined

**Step 3: Write minimal implementation**

```rust
// crates/ssdid-sdk-core/src/error.rs
use std::fmt;

#[derive(Debug, Clone, PartialEq)]
pub enum SdkError {
    InvalidConfig(String),
    InvalidUrl(String),
    InvalidFormat(String),
    ResponseParseError(String),
    QrGenerationFailed(String),
}

impl fmt::Display for SdkError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            SdkError::InvalidConfig(msg) => write!(f, "Invalid configuration: {msg}"),
            SdkError::InvalidUrl(msg) => write!(f, "Invalid URL: {msg}"),
            SdkError::InvalidFormat(msg) => write!(f, "Invalid credential format: {msg}"),
            SdkError::ResponseParseError(msg) => write!(f, "Response parse error: {msg}"),
            SdkError::QrGenerationFailed(msg) => write!(f, "QR generation failed: {msg}"),
        }
    }
}

impl std::error::Error for SdkError {}

pub type SdkResult<T> = Result<T, SdkError>;

// tests at bottom...
```

**Step 4: Run test to verify it passes**

Run: `cargo test -p ssdid-sdk-core`
Expected: PASS (4 tests)

**Step 5: Commit**

```bash
git add crates/ssdid-sdk-core/src/error.rs
git commit -m "feat(core): add SDK error types"
```

---

## Task 3: DCQL Query Builder

**Files:**
- Create: `crates/ssdid-sdk-core/src/dcql.rs`
- Test: inline `#[cfg(test)]`

**Step 1: Write the failing tests**

```rust
// crates/ssdid-sdk-core/src/dcql.rs
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn build_sd_jwt_dcql_with_required_and_optional_claims() {
        let dcql = DcqlBuilder::new()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .claim("email", ClaimPriority::Optional)
                .done()
            .build();

        let json = dcql.to_json();
        let parsed: serde_json::Value = serde_json::from_str(&json).unwrap();

        let creds = parsed["credentials"].as_array().unwrap();
        assert_eq!(creds.len(), 1);
        assert_eq!(creds[0]["format"], "vc+sd-jwt");
        assert_eq!(creds[0]["meta"]["vct_values"][0], "IdentityCredential");

        let claims = creds[0]["claims"].as_array().unwrap();
        assert_eq!(claims.len(), 2);
        assert_eq!(claims[0]["path"][0], "name");
        assert!(claims[0].get("optional").is_none());
        assert_eq!(claims[1]["path"][0], "email");
        assert_eq!(claims[1]["optional"], true);
    }

    #[test]
    fn build_mdoc_dcql() {
        let dcql = DcqlBuilder::new()
            .credential("org.iso.18013.5.1.mDL", CredentialFormat::MsoMdoc)
                .claim("org.iso.18013.5.1/family_name", ClaimPriority::Required)
                .claim("org.iso.18013.5.1/birth_date", ClaimPriority::Optional)
                .done()
            .build();

        let json = dcql.to_json();
        let parsed: serde_json::Value = serde_json::from_str(&json).unwrap();

        let creds = parsed["credentials"].as_array().unwrap();
        assert_eq!(creds[0]["format"], "mso_mdoc");
        assert_eq!(creds[0]["meta"]["doctype_value"], "org.iso.18013.5.1.mDL");

        let claims = creds[0]["claims"].as_array().unwrap();
        assert_eq!(claims[0]["namespace"], "org.iso.18013.5.1");
        assert_eq!(claims[0]["claim_name"], "family_name");
    }

    #[test]
    fn build_multiple_credentials() {
        let dcql = DcqlBuilder::new()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .done()
            .credential("org.iso.18013.5.1.mDL", CredentialFormat::MsoMdoc)
                .claim("org.iso.18013.5.1/family_name", ClaimPriority::Required)
                .done()
            .build();

        let json = dcql.to_json();
        let parsed: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert_eq!(parsed["credentials"].as_array().unwrap().len(), 2);
    }

    #[test]
    fn credential_gets_auto_generated_id() {
        let dcql = DcqlBuilder::new()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .done()
            .build();

        let json = dcql.to_json();
        let parsed: serde_json::Value = serde_json::from_str(&json).unwrap();
        let id = parsed["credentials"][0]["id"].as_str().unwrap();
        assert!(!id.is_empty());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cargo test -p ssdid-sdk-core`
Expected: FAIL — `DcqlBuilder` not defined

**Step 3: Write minimal implementation**

```rust
// crates/ssdid-sdk-core/src/dcql.rs
use serde::Serialize;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum CredentialFormat {
    SdJwt,
    MsoMdoc,
}

impl CredentialFormat {
    pub fn as_str(&self) -> &'static str {
        match self {
            CredentialFormat::SdJwt => "vc+sd-jwt",
            CredentialFormat::MsoMdoc => "mso_mdoc",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ClaimPriority {
    Required,
    Optional,
}

#[derive(Debug, Clone, Serialize)]
pub struct DcqlQuery {
    pub credentials: Vec<serde_json::Value>,
}

impl DcqlQuery {
    pub fn to_json(&self) -> String {
        serde_json::to_string(self).unwrap()
    }
}

pub struct DcqlBuilder {
    credentials: Vec<serde_json::Value>,
}

impl DcqlBuilder {
    pub fn new() -> Self {
        Self { credentials: vec![] }
    }

    pub fn credential(self, type_or_doctype: &str, format: CredentialFormat) -> CredentialBuilder {
        CredentialBuilder {
            parent: self,
            type_or_doctype: type_or_doctype.to_string(),
            format,
            claims: vec![],
        }
    }

    pub fn build(self) -> DcqlQuery {
        DcqlQuery { credentials: self.credentials }
    }
}

pub struct CredentialBuilder {
    parent: DcqlBuilder,
    type_or_doctype: String,
    format: CredentialFormat,
    claims: Vec<(String, ClaimPriority)>,
}

impl CredentialBuilder {
    pub fn claim(mut self, name: &str, priority: ClaimPriority) -> Self {
        self.claims.push((name.to_string(), priority));
        self
    }

    pub fn done(mut self) -> DcqlBuilder {
        let id = format!("req-{}", self.parent.credentials.len() + 1);

        let claims_json: Vec<serde_json::Value> = match self.format {
            CredentialFormat::SdJwt => {
                self.claims.iter().map(|(name, priority)| {
                    let mut claim = serde_json::json!({"path": [name]});
                    if *priority == ClaimPriority::Optional {
                        claim["optional"] = serde_json::json!(true);
                    }
                    claim
                }).collect()
            }
            CredentialFormat::MsoMdoc => {
                self.claims.iter().map(|(name, priority)| {
                    let parts: Vec<&str> = name.splitn(2, '/').collect();
                    let (namespace, claim_name) = if parts.len() == 2 {
                        (parts[0], parts[1])
                    } else {
                        ("", parts[0])
                    };
                    let mut claim = serde_json::json!({
                        "namespace": namespace,
                        "claim_name": claim_name
                    });
                    if *priority == ClaimPriority::Optional {
                        claim["optional"] = serde_json::json!(true);
                    }
                    claim
                }).collect()
            }
        };

        let meta = match self.format {
            CredentialFormat::SdJwt => serde_json::json!({"vct_values": [self.type_or_doctype]}),
            CredentialFormat::MsoMdoc => serde_json::json!({"doctype_value": self.type_or_doctype}),
        };

        let credential = serde_json::json!({
            "id": id,
            "format": self.format.as_str(),
            "meta": meta,
            "claims": claims_json
        });

        self.parent.credentials.push(credential);
        self.parent
    }
}

// tests at bottom...
```

**Step 4: Run test to verify it passes**

Run: `cargo test -p ssdid-sdk-core`
Expected: PASS (all tests)

**Step 5: Commit**

```bash
git add crates/ssdid-sdk-core/src/dcql.rs
git commit -m "feat(core): add DCQL query builder with SD-JWT and mdoc support"
```

---

## Task 4: Credential Request Builder

**Files:**
- Create: `crates/ssdid-sdk-core/src/credential_request.rs`
- Test: inline `#[cfg(test)]`

**Step 1: Write the failing tests**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use crate::dcql::{CredentialFormat, ClaimPriority};

    #[test]
    fn build_credential_request_produces_valid_deeplink() {
        let request = SsdidCredentialRequest::builder()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .done()
            .response_url("https://myapp.com/api/ssdid/response")
            .callback_scheme("myapp://ssdid/callback")
            .build()
            .unwrap();

        let deeplink = request.to_deeplink();
        assert!(deeplink.starts_with("ssdid://authorize?"));
        assert!(deeplink.contains("dcql_query="));
        assert!(deeplink.contains("response_url="));
        assert!(deeplink.contains("callback_scheme="));
        assert!(deeplink.contains("nonce="));
    }

    #[test]
    fn build_fails_without_response_url() {
        let result = SsdidCredentialRequest::builder()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .done()
            .callback_scheme("myapp://ssdid/callback")
            .build();

        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), crate::error::SdkError::InvalidConfig(_)));
    }

    #[test]
    fn build_fails_without_callback_scheme() {
        let result = SsdidCredentialRequest::builder()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .done()
            .response_url("https://myapp.com/api/ssdid/response")
            .build();

        assert!(result.is_err());
    }

    #[test]
    fn build_fails_with_http_response_url() {
        let result = SsdidCredentialRequest::builder()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .done()
            .response_url("http://insecure.com/response")
            .callback_scheme("myapp://ssdid/callback")
            .build();

        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), crate::error::SdkError::InvalidUrl(_)));
    }

    #[test]
    fn build_fails_without_credentials() {
        let result = SsdidCredentialRequest::builder()
            .response_url("https://myapp.com/api/ssdid/response")
            .callback_scheme("myapp://ssdid/callback")
            .build();

        assert!(result.is_err());
    }

    #[test]
    fn extra_params_included_in_deeplink() {
        let request = SsdidCredentialRequest::builder()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .done()
            .response_url("https://myapp.com/api/ssdid/response")
            .callback_scheme("myapp://ssdid/callback")
            .extra("tenant_id", "org-123")
            .build()
            .unwrap();

        let deeplink = request.to_deeplink();
        assert!(deeplink.contains("tenant_id=org-123"));
    }

    #[test]
    fn qr_string_matches_deeplink() {
        let request = SsdidCredentialRequest::builder()
            .credential("IdentityCredential", CredentialFormat::SdJwt)
                .claim("name", ClaimPriority::Required)
                .done()
            .response_url("https://myapp.com/api/ssdid/response")
            .callback_scheme("myapp://ssdid/callback")
            .build()
            .unwrap();

        assert_eq!(request.to_qr_string(), request.to_deeplink());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cargo test -p ssdid-sdk-core`
Expected: FAIL — `SsdidCredentialRequest` not defined

**Step 3: Write minimal implementation**

```rust
// crates/ssdid-sdk-core/src/credential_request.rs
use crate::dcql::{ClaimPriority, CredentialFormat, DcqlBuilder, CredentialBuilder};
use crate::error::{SdkError, SdkResult};

pub struct SsdidCredentialRequest {
    deeplink: String,
}

impl SsdidCredentialRequest {
    pub fn builder() -> CredentialRequestBuilder {
        CredentialRequestBuilder::new()
    }

    pub fn to_deeplink(&self) -> String {
        self.deeplink.clone()
    }

    pub fn to_qr_string(&self) -> String {
        self.deeplink.clone()
    }
}

pub struct CredentialRequestBuilder {
    dcql_builder: DcqlBuilder,
    response_url: Option<String>,
    callback_scheme: Option<String>,
    extras: Vec<(String, String)>,
}

impl CredentialRequestBuilder {
    fn new() -> Self {
        Self {
            dcql_builder: DcqlBuilder::new(),
            response_url: None,
            callback_scheme: None,
            extras: vec![],
        }
    }

    pub fn credential(self, type_or_doctype: &str, format: CredentialFormat) -> CredentialRequestCredentialBuilder {
        let cred_builder = self.dcql_builder.credential(type_or_doctype, format);
        CredentialRequestCredentialBuilder {
            inner: cred_builder,
            response_url: self.response_url,
            callback_scheme: self.callback_scheme,
            extras: self.extras,
        }
    }

    pub fn response_url(mut self, url: &str) -> Self {
        self.response_url = Some(url.to_string());
        self
    }

    pub fn callback_scheme(mut self, scheme: &str) -> Self {
        self.callback_scheme = Some(scheme.to_string());
        self
    }

    pub fn extra(mut self, key: &str, value: &str) -> Self {
        self.extras.push((key.to_string(), value.to_string()));
        self
    }

    pub fn build(self) -> SdkResult<SsdidCredentialRequest> {
        let response_url = self.response_url
            .ok_or_else(|| SdkError::InvalidConfig("response_url is required".into()))?;

        if !response_url.starts_with("https://") {
            return Err(SdkError::InvalidUrl(format!("response_url must be HTTPS: {response_url}")));
        }

        let callback_scheme = self.callback_scheme
            .ok_or_else(|| SdkError::InvalidConfig("callback_scheme is required".into()))?;

        let dcql = self.dcql_builder.build();
        if dcql.credentials.is_empty() {
            return Err(SdkError::InvalidConfig("at least one credential is required".into()));
        }

        let nonce = uuid::Uuid::new_v4().to_string();
        let dcql_json = dcql.to_json();

        let mut url = url::Url::parse("ssdid://authorize").unwrap();
        {
            let mut params = url.query_pairs_mut();
            params.append_pair("dcql_query", &dcql_json);
            params.append_pair("response_url", &response_url);
            params.append_pair("callback_scheme", &callback_scheme);
            params.append_pair("nonce", &nonce);
            for (k, v) in &self.extras {
                params.append_pair(k, v);
            }
        }

        Ok(SsdidCredentialRequest { deeplink: url.to_string() })
    }
}

/// Intermediate builder that wraps CredentialBuilder and returns to CredentialRequestBuilder
pub struct CredentialRequestCredentialBuilder {
    inner: CredentialBuilder,
    response_url: Option<String>,
    callback_scheme: Option<String>,
    extras: Vec<(String, String)>,
}

impl CredentialRequestCredentialBuilder {
    pub fn claim(mut self, name: &str, priority: ClaimPriority) -> Self {
        self.inner = self.inner.claim(name, priority);
        self
    }

    pub fn done(self) -> CredentialRequestBuilder {
        let dcql_builder = self.inner.done();
        CredentialRequestBuilder {
            dcql_builder,
            response_url: self.response_url,
            callback_scheme: self.callback_scheme,
            extras: self.extras,
        }
    }
}

// tests at bottom...
```

**Step 4: Run test to verify it passes**

Run: `cargo test -p ssdid-sdk-core`
Expected: PASS (all tests)

**Step 5: Commit**

```bash
git add crates/ssdid-sdk-core/src/credential_request.rs
git commit -m "feat(core): add credential request builder with validation"
```

---

## Task 5: Auth Request Builder

**Files:**
- Create: `crates/ssdid-sdk-core/src/auth_request.rs`
- Test: inline `#[cfg(test)]`

**Step 1: Write the failing tests**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn build_auth_request_deeplink() {
        let auth = SsdidAuthRequest::builder()
            .challenge("test-challenge-123")
            .accepted_algorithms(&["Ed25519", "EcdsaP256"])
            .response_url("https://myapp.com/api/ssdid/auth/response")
            .callback_scheme("myapp://ssdid/auth/callback")
            .build()
            .unwrap();

        let deeplink = auth.to_deeplink();
        assert!(deeplink.starts_with("ssdid://authenticate?"));
        assert!(deeplink.contains("challenge=test-challenge-123"));
        assert!(deeplink.contains("accepted_algorithms="));
        assert!(deeplink.contains("Ed25519"));
        assert!(deeplink.contains("response_url="));
        assert!(deeplink.contains("callback_scheme="));
    }

    #[test]
    fn build_fails_without_challenge() {
        let result = SsdidAuthRequest::builder()
            .accepted_algorithms(&["Ed25519"])
            .response_url("https://myapp.com/api/ssdid/auth/response")
            .callback_scheme("myapp://ssdid/auth/callback")
            .build();

        assert!(result.is_err());
    }

    #[test]
    fn build_fails_with_http_response_url() {
        let result = SsdidAuthRequest::builder()
            .challenge("c")
            .accepted_algorithms(&["Ed25519"])
            .response_url("http://insecure.com/response")
            .callback_scheme("myapp://ssdid/auth/callback")
            .build();

        assert!(result.is_err());
    }

    #[test]
    fn extra_params_included() {
        let auth = SsdidAuthRequest::builder()
            .challenge("c")
            .accepted_algorithms(&["Ed25519"])
            .response_url("https://myapp.com/api/ssdid/auth/response")
            .callback_scheme("myapp://ssdid/auth/callback")
            .extra("session_id", "sess-456")
            .build()
            .unwrap();

        assert!(auth.to_deeplink().contains("session_id=sess-456"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cargo test -p ssdid-sdk-core`
Expected: FAIL — `SsdidAuthRequest` not defined

**Step 3: Write minimal implementation**

```rust
// crates/ssdid-sdk-core/src/auth_request.rs
use crate::error::{SdkError, SdkResult};

pub struct SsdidAuthRequest {
    deeplink: String,
}

impl SsdidAuthRequest {
    pub fn builder() -> AuthRequestBuilder {
        AuthRequestBuilder::new()
    }

    pub fn to_deeplink(&self) -> String {
        self.deeplink.clone()
    }

    pub fn to_qr_string(&self) -> String {
        self.deeplink.clone()
    }
}

pub struct AuthRequestBuilder {
    challenge: Option<String>,
    accepted_algorithms: Vec<String>,
    response_url: Option<String>,
    callback_scheme: Option<String>,
    extras: Vec<(String, String)>,
}

impl AuthRequestBuilder {
    fn new() -> Self {
        Self {
            challenge: None,
            accepted_algorithms: vec![],
            response_url: None,
            callback_scheme: None,
            extras: vec![],
        }
    }

    pub fn challenge(mut self, challenge: &str) -> Self {
        self.challenge = Some(challenge.to_string());
        self
    }

    pub fn accepted_algorithms(mut self, algorithms: &[&str]) -> Self {
        self.accepted_algorithms = algorithms.iter().map(|s| s.to_string()).collect();
        self
    }

    pub fn response_url(mut self, url: &str) -> Self {
        self.response_url = Some(url.to_string());
        self
    }

    pub fn callback_scheme(mut self, scheme: &str) -> Self {
        self.callback_scheme = Some(scheme.to_string());
        self
    }

    pub fn extra(mut self, key: &str, value: &str) -> Self {
        self.extras.push((key.to_string(), value.to_string()));
        self
    }

    pub fn build(self) -> SdkResult<SsdidAuthRequest> {
        let challenge = self.challenge
            .ok_or_else(|| SdkError::InvalidConfig("challenge is required".into()))?;

        let response_url = self.response_url
            .ok_or_else(|| SdkError::InvalidConfig("response_url is required".into()))?;

        if !response_url.starts_with("https://") {
            return Err(SdkError::InvalidUrl(format!("response_url must be HTTPS: {response_url}")));
        }

        let callback_scheme = self.callback_scheme
            .ok_or_else(|| SdkError::InvalidConfig("callback_scheme is required".into()))?;

        let algorithms_csv = self.accepted_algorithms.join(",");

        let mut url = url::Url::parse("ssdid://authenticate").unwrap();
        {
            let mut params = url.query_pairs_mut();
            params.append_pair("challenge", &challenge);
            if !algorithms_csv.is_empty() {
                params.append_pair("accepted_algorithms", &algorithms_csv);
            }
            params.append_pair("response_url", &response_url);
            params.append_pair("callback_scheme", &callback_scheme);
            for (k, v) in &self.extras {
                params.append_pair(k, v);
            }
        }

        Ok(SsdidAuthRequest { deeplink: url.to_string() })
    }
}

// tests at bottom...
```

**Step 4: Run test to verify it passes**

Run: `cargo test -p ssdid-sdk-core`
Expected: PASS

**Step 5: Commit**

```bash
git add crates/ssdid-sdk-core/src/auth_request.rs
git commit -m "feat(core): add auth request builder with challenge and algorithms"
```

---

## Task 6: Credential Offer Builder

**Files:**
- Create: `crates/ssdid-sdk-core/src/credential_offer.rs`
- Test: inline `#[cfg(test)]`

**Step 1: Write the failing tests**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use crate::dcql::CredentialFormat;

    #[test]
    fn build_credential_offer_deeplink() {
        let offer = SsdidCredentialOffer::builder()
            .issuer_url("https://myapp.com")
            .credential_type("EmployeeBadge", CredentialFormat::SdJwt)
            .pre_authorized_code("code-123")
            .callback_scheme("myapp://ssdid/issuance/callback")
            .build()
            .unwrap();

        let deeplink = offer.to_deeplink();
        assert!(deeplink.starts_with("openid-credential-offer://?"));
        assert!(deeplink.contains("credential_offer_uri=") || deeplink.contains("credential_offer="));
    }

    #[test]
    fn build_fails_without_issuer_url() {
        let result = SsdidCredentialOffer::builder()
            .credential_type("EmployeeBadge", CredentialFormat::SdJwt)
            .pre_authorized_code("code-123")
            .callback_scheme("myapp://ssdid/issuance/callback")
            .build();

        assert!(result.is_err());
    }

    #[test]
    fn build_fails_without_pre_authorized_code() {
        let result = SsdidCredentialOffer::builder()
            .issuer_url("https://myapp.com")
            .credential_type("EmployeeBadge", CredentialFormat::SdJwt)
            .callback_scheme("myapp://ssdid/issuance/callback")
            .build();

        assert!(result.is_err());
    }

    #[test]
    fn offer_contains_credential_type_and_format() {
        let offer = SsdidCredentialOffer::builder()
            .issuer_url("https://myapp.com")
            .credential_type("EmployeeBadge", CredentialFormat::SdJwt)
            .pre_authorized_code("code-123")
            .callback_scheme("myapp://ssdid/issuance/callback")
            .build()
            .unwrap();

        let deeplink = offer.to_deeplink();
        // The offer JSON should contain credential config info
        assert!(deeplink.contains("EmployeeBadge"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cargo test -p ssdid-sdk-core`
Expected: FAIL

**Step 3: Write minimal implementation**

```rust
// crates/ssdid-sdk-core/src/credential_offer.rs
use crate::dcql::CredentialFormat;
use crate::error::{SdkError, SdkResult};

pub struct SsdidCredentialOffer {
    deeplink: String,
}

impl SsdidCredentialOffer {
    pub fn builder() -> CredentialOfferBuilder {
        CredentialOfferBuilder::new()
    }

    pub fn to_deeplink(&self) -> String {
        self.deeplink.clone()
    }

    pub fn to_qr_string(&self) -> String {
        self.deeplink.clone()
    }
}

pub struct CredentialOfferBuilder {
    issuer_url: Option<String>,
    credential_type: Option<String>,
    credential_format: Option<CredentialFormat>,
    pre_authorized_code: Option<String>,
    callback_scheme: Option<String>,
}

impl CredentialOfferBuilder {
    fn new() -> Self {
        Self {
            issuer_url: None,
            credential_type: None,
            credential_format: None,
            pre_authorized_code: None,
            callback_scheme: None,
        }
    }

    pub fn issuer_url(mut self, url: &str) -> Self {
        self.issuer_url = Some(url.to_string());
        self
    }

    pub fn credential_type(mut self, cred_type: &str, format: CredentialFormat) -> Self {
        self.credential_type = Some(cred_type.to_string());
        self.credential_format = Some(format);
        self
    }

    pub fn pre_authorized_code(mut self, code: &str) -> Self {
        self.pre_authorized_code = Some(code.to_string());
        self
    }

    pub fn callback_scheme(mut self, scheme: &str) -> Self {
        self.callback_scheme = Some(scheme.to_string());
        self
    }

    pub fn build(self) -> SdkResult<SsdidCredentialOffer> {
        let issuer_url = self.issuer_url
            .ok_or_else(|| SdkError::InvalidConfig("issuer_url is required".into()))?;

        let credential_type = self.credential_type
            .ok_or_else(|| SdkError::InvalidConfig("credential_type is required".into()))?;

        let credential_format = self.credential_format
            .ok_or_else(|| SdkError::InvalidConfig("credential_format is required".into()))?;

        let pre_authorized_code = self.pre_authorized_code
            .ok_or_else(|| SdkError::InvalidConfig("pre_authorized_code is required".into()))?;

        let _callback_scheme = self.callback_scheme
            .ok_or_else(|| SdkError::InvalidConfig("callback_scheme is required".into()))?;

        let offer_json = serde_json::json!({
            "credential_issuer": issuer_url,
            "credential_configuration_ids": [credential_type],
            "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                    "pre-authorized_code": pre_authorized_code
                }
            }
        });

        let offer_encoded = url::form_urlencoded::Serializer::new(String::new())
            .append_pair("credential_offer", &offer_json.to_string())
            .finish();

        let deeplink = format!("openid-credential-offer://?{offer_encoded}");

        Ok(SsdidCredentialOffer { deeplink })
    }
}

// tests at bottom...
```

**Step 4: Run test to verify it passes**

Run: `cargo test -p ssdid-sdk-core`
Expected: PASS

**Step 5: Commit**

```bash
git add crates/ssdid-sdk-core/src/credential_offer.rs
git commit -m "feat(core): add credential offer builder for OID4VCI"
```

---

## Task 7: Callback Parser

**Files:**
- Create: `crates/ssdid-sdk-core/src/callback.rs`
- Test: inline `#[cfg(test)]`

**Step 1: Write the failing tests**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_success_callback() {
        let uri = "myapp://ssdid/callback?response_id=abc123&status=success";
        let callback = SsdidCallback::parse(uri).unwrap();
        assert!(matches!(callback, SsdidCallback::Success { response_id } if response_id == "abc123"));
    }

    #[test]
    fn parse_denied_callback() {
        let uri = "myapp://ssdid/callback?status=denied";
        let callback = SsdidCallback::parse(uri).unwrap();
        assert!(matches!(callback, SsdidCallback::Denied));
    }

    #[test]
    fn parse_error_callback() {
        let uri = "myapp://ssdid/callback?status=error&error=no_matching_credentials";
        let callback = SsdidCallback::parse(uri).unwrap();
        assert!(matches!(callback, SsdidCallback::Error { code } if code == "no_matching_credentials"));
    }

    #[test]
    fn parse_issuance_success() {
        let uri = "myapp://ssdid/callback?status=success&credential_id=cred-xyz";
        let callback = SsdidCallback::parse(uri).unwrap();
        match callback {
            SsdidCallback::Success { response_id } => {
                // credential_id treated as response_id in success case
                assert!(response_id == "cred-xyz" || response_id.is_empty());
            }
            _ => panic!("expected Success"),
        }
    }

    #[test]
    fn parse_missing_status_returns_error() {
        let uri = "myapp://ssdid/callback?foo=bar";
        let result = SsdidCallback::parse(uri);
        assert!(result.is_err());
    }

    #[test]
    fn parse_unknown_status_returns_error() {
        let uri = "myapp://ssdid/callback?status=unknown_value";
        let result = SsdidCallback::parse(uri);
        assert!(result.is_err());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cargo test -p ssdid-sdk-core`
Expected: FAIL

**Step 3: Write minimal implementation**

```rust
// crates/ssdid-sdk-core/src/callback.rs
use crate::error::{SdkError, SdkResult};
use std::collections::HashMap;

#[derive(Debug, Clone, PartialEq)]
pub enum SsdidCallback {
    Success { response_id: String },
    Denied,
    Error { code: String },
}

impl SsdidCallback {
    pub fn parse(uri: &str) -> SdkResult<Self> {
        let params = Self::extract_params(uri);

        let status = params.get("status")
            .ok_or_else(|| SdkError::ResponseParseError("missing status parameter".into()))?;

        match status.as_str() {
            "success" => {
                let response_id = params.get("response_id")
                    .or_else(|| params.get("credential_id"))
                    .cloned()
                    .unwrap_or_default();
                Ok(SsdidCallback::Success { response_id })
            }
            "denied" => Ok(SsdidCallback::Denied),
            "error" => {
                let code = params.get("error")
                    .cloned()
                    .unwrap_or_else(|| "unknown".into());
                Ok(SsdidCallback::Error { code })
            }
            other => Err(SdkError::ResponseParseError(
                format!("unknown status: {other}")
            )),
        }
    }

    fn extract_params(uri: &str) -> HashMap<String, String> {
        let query = uri.split('?').nth(1).unwrap_or("");
        url::form_urlencoded::parse(query.as_bytes())
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect()
    }
}

// tests at bottom...
```

**Step 4: Run test to verify it passes**

Run: `cargo test -p ssdid-sdk-core`
Expected: PASS

**Step 5: Commit**

```bash
git add crates/ssdid-sdk-core/src/callback.rs
git commit -m "feat(core): add callback URI parser for success/denied/error"
```

---

## Task 8: Response Parser

**Files:**
- Create: `crates/ssdid-sdk-core/src/response_parser.rs`
- Test: inline `#[cfg(test)]`

**Step 1: Write the failing tests**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_vp_response_extracts_token() {
        let body = "vp_token=eyJhbGciOiJFZERTQSJ9.eyJ2Y3QiOiJJZGVudGl0eUNyZWRlbnRpYWwifQ.sig~disc1~disc2~&presentation_submission=%7B%22id%22%3A%22ps-1%22%7D&nonce=n-1";
        let response = SsdidResponse::parse_vp_response(body).unwrap();
        assert!(response.vp_token.starts_with("eyJ"));
        assert_eq!(response.nonce, Some("n-1".into()));
    }

    #[test]
    fn parse_vp_response_missing_token_fails() {
        let body = "presentation_submission=%7B%7D&nonce=n-1";
        let result = SsdidResponse::parse_vp_response(body);
        assert!(result.is_err());
    }

    #[test]
    fn parse_json_response() {
        let json = r#"{"vp_token":"token-value","nonce":"n-1"}"#;
        let response = SsdidResponse::parse_json_response(json).unwrap();
        assert_eq!(response.vp_token, "token-value");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cargo test -p ssdid-sdk-core`
Expected: FAIL

**Step 3: Write minimal implementation**

```rust
// crates/ssdid-sdk-core/src/response_parser.rs
use crate::error::{SdkError, SdkResult};
use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct SsdidResponse {
    pub vp_token: String,
    pub presentation_submission: Option<String>,
    pub nonce: Option<String>,
}

impl SsdidResponse {
    /// Parse a form-encoded direct_post body (what the backend receives)
    pub fn parse_vp_response(body: &str) -> SdkResult<Self> {
        let params: HashMap<String, String> = url::form_urlencoded::parse(body.as_bytes())
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect();

        let vp_token = params.get("vp_token")
            .ok_or_else(|| SdkError::ResponseParseError("missing vp_token".into()))?
            .clone();

        Ok(Self {
            vp_token,
            presentation_submission: params.get("presentation_submission").cloned(),
            nonce: params.get("nonce").cloned(),
        })
    }

    /// Parse a JSON response body
    pub fn parse_json_response(json: &str) -> SdkResult<Self> {
        let obj: serde_json::Value = serde_json::from_str(json)
            .map_err(|e| SdkError::ResponseParseError(e.to_string()))?;

        let vp_token = obj["vp_token"].as_str()
            .ok_or_else(|| SdkError::ResponseParseError("missing vp_token".into()))?
            .to_string();

        Ok(Self {
            vp_token,
            presentation_submission: obj.get("presentation_submission")
                .map(|v| v.to_string()),
            nonce: obj["nonce"].as_str().map(|s| s.to_string()),
        })
    }
}

// tests at bottom...
```

**Step 4: Run test to verify it passes**

Run: `cargo test -p ssdid-sdk-core`
Expected: PASS

**Step 5: Commit**

```bash
git add crates/ssdid-sdk-core/src/response_parser.rs
git commit -m "feat(core): add VP response parser for form-encoded and JSON"
```

---

## Task 9: QR Code Generator

**Files:**
- Create: `crates/ssdid-sdk-qr/src/qr_generator.rs`
- Modify: `crates/ssdid-sdk-qr/src/lib.rs`
- Test: inline `#[cfg(test)]`

**Step 1: Write the failing tests**

```rust
// crates/ssdid-sdk-qr/src/qr_generator.rs
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generate_qr_png_bytes() {
        let data = "ssdid://authorize?dcql_query=%7B%7D&response_url=https%3A%2F%2Fexample.com";
        let png = generate_qr_png(data, 300).unwrap();
        // PNG magic bytes
        assert_eq!(&png[0..4], &[0x89, 0x50, 0x4E, 0x47]);
        assert!(png.len() > 100);
    }

    #[test]
    fn generate_qr_png_different_sizes() {
        let data = "ssdid://authorize?test=1";
        let small = generate_qr_png(data, 100).unwrap();
        let large = generate_qr_png(data, 500).unwrap();
        assert!(large.len() > small.len());
    }

    #[test]
    fn generate_qr_fails_on_empty_data() {
        let result = generate_qr_png("", 300);
        assert!(result.is_err());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cargo test -p ssdid-sdk-qr`
Expected: FAIL

**Step 3: Write minimal implementation**

```rust
// crates/ssdid-sdk-qr/src/qr_generator.rs
use ssdid_sdk_core::error::{SdkError, SdkResult};
use qrcode::QrCode;
use image::Luma;
use std::io::Cursor;

pub fn generate_qr_png(data: &str, size: u32) -> SdkResult<Vec<u8>> {
    if data.is_empty() {
        return Err(SdkError::QrGenerationFailed("data cannot be empty".into()));
    }

    let code = QrCode::new(data.as_bytes())
        .map_err(|e| SdkError::QrGenerationFailed(e.to_string()))?;

    let image = code.render::<Luma<u8>>()
        .min_dimensions(size, size)
        .build();

    let mut png_bytes = Vec::new();
    let mut cursor = Cursor::new(&mut png_bytes);
    image.write_to(&mut cursor, image::ImageFormat::Png)
        .map_err(|e| SdkError::QrGenerationFailed(e.to_string()))?;

    Ok(png_bytes)
}
```

```rust
// crates/ssdid-sdk-qr/src/lib.rs
pub mod qr_generator;
pub use qr_generator::generate_qr_png;
```

**Step 4: Run test to verify it passes**

Run: `cargo test -p ssdid-sdk-qr`
Expected: PASS

**Step 5: Commit**

```bash
git add crates/ssdid-sdk-qr/
git commit -m "feat(qr): add QR code PNG generator"
```

---

## Task 10: UniFFI Bindings

**Files:**
- Modify: `crates/ssdid-sdk-ffi/src/lib.rs`
- Create: `crates/ssdid-sdk-ffi/uniffi-bindgen.rs`
- Test: `cargo build` verifies FFI compiles

**Step 1: Write the FFI layer**

```rust
// crates/ssdid-sdk-ffi/src/lib.rs
uniffi::setup_scaffolding!();

use ssdid_sdk_core::dcql::{CredentialFormat, ClaimPriority};
use ssdid_sdk_core::error::SdkError;

// Re-export enums with UniFFI derive
#[derive(uniffi::Enum)]
pub enum FfiCredentialFormat {
    SdJwt,
    MsoMdoc,
}

impl From<FfiCredentialFormat> for CredentialFormat {
    fn from(f: FfiCredentialFormat) -> Self {
        match f {
            FfiCredentialFormat::SdJwt => CredentialFormat::SdJwt,
            FfiCredentialFormat::MsoMdoc => CredentialFormat::MsoMdoc,
        }
    }
}

#[derive(uniffi::Enum)]
pub enum FfiClaimPriority {
    Required,
    Optional,
}

impl From<FfiClaimPriority> for ClaimPriority {
    fn from(p: FfiClaimPriority) -> Self {
        match p {
            FfiClaimPriority::Required => ClaimPriority::Required,
            FfiClaimPriority::Optional => ClaimPriority::Optional,
        }
    }
}

#[derive(uniffi::Error, Debug)]
pub enum FfiSdkError {
    InvalidConfig { message: String },
    InvalidUrl { message: String },
    InvalidFormat { message: String },
    ResponseParseError { message: String },
    QrGenerationFailed { message: String },
}

impl From<SdkError> for FfiSdkError {
    fn from(e: SdkError) -> Self {
        match e {
            SdkError::InvalidConfig(m) => FfiSdkError::InvalidConfig { message: m },
            SdkError::InvalidUrl(m) => FfiSdkError::InvalidUrl { message: m },
            SdkError::InvalidFormat(m) => FfiSdkError::InvalidFormat { message: m },
            SdkError::ResponseParseError(m) => FfiSdkError::ResponseParseError { message: m },
            SdkError::QrGenerationFailed(m) => FfiSdkError::QrGenerationFailed { message: m },
        }
    }
}

#[derive(uniffi::Enum)]
pub enum FfiCallback {
    Success { response_id: String },
    Denied,
    Error { code: String },
}

// --- Exported functions ---

#[uniffi::export]
pub fn build_credential_request(
    credential_type: String,
    format: FfiCredentialFormat,
    claims: Vec<FfiClaimEntry>,
    response_url: String,
    callback_scheme: String,
    extras: Vec<FfiKeyValue>,
) -> Result<String, FfiSdkError> {
    use ssdid_sdk_core::credential_request::SsdidCredentialRequest;

    let mut builder = SsdidCredentialRequest::builder()
        .credential(&credential_type, format.into());

    for claim in &claims {
        builder = builder.claim(&claim.name, claim.priority.clone().into());
    }

    let mut req_builder = builder.done()
        .response_url(&response_url)
        .callback_scheme(&callback_scheme);

    for kv in &extras {
        req_builder = req_builder.extra(&kv.key, &kv.value);
    }

    let request = req_builder.build().map_err(FfiSdkError::from)?;
    Ok(request.to_deeplink())
}

#[uniffi::export]
pub fn build_auth_request(
    challenge: String,
    accepted_algorithms: Vec<String>,
    response_url: String,
    callback_scheme: String,
    extras: Vec<FfiKeyValue>,
) -> Result<String, FfiSdkError> {
    use ssdid_sdk_core::auth_request::SsdidAuthRequest;

    let algo_refs: Vec<&str> = accepted_algorithms.iter().map(|s| s.as_str()).collect();
    let mut builder = SsdidAuthRequest::builder()
        .challenge(&challenge)
        .accepted_algorithms(&algo_refs)
        .response_url(&response_url)
        .callback_scheme(&callback_scheme);

    for kv in &extras {
        builder = builder.extra(&kv.key, &kv.value);
    }

    let auth = builder.build().map_err(FfiSdkError::from)?;
    Ok(auth.to_deeplink())
}

#[uniffi::export]
pub fn build_credential_offer(
    issuer_url: String,
    credential_type: String,
    format: FfiCredentialFormat,
    pre_authorized_code: String,
    callback_scheme: String,
) -> Result<String, FfiSdkError> {
    use ssdid_sdk_core::credential_offer::SsdidCredentialOffer;

    let offer = SsdidCredentialOffer::builder()
        .issuer_url(&issuer_url)
        .credential_type(&credential_type, format.into())
        .pre_authorized_code(&pre_authorized_code)
        .callback_scheme(&callback_scheme)
        .build()
        .map_err(FfiSdkError::from)?;

    Ok(offer.to_deeplink())
}

#[uniffi::export]
pub fn parse_callback(uri: String) -> Result<FfiCallback, FfiSdkError> {
    use ssdid_sdk_core::callback::SsdidCallback;

    let callback = SsdidCallback::parse(&uri).map_err(FfiSdkError::from)?;
    Ok(match callback {
        SsdidCallback::Success { response_id } => FfiCallback::Success { response_id },
        SsdidCallback::Denied => FfiCallback::Denied,
        SsdidCallback::Error { code } => FfiCallback::Error { code },
    })
}

#[uniffi::export]
pub fn parse_vp_response(body: String) -> Result<FfiVpResponse, FfiSdkError> {
    use ssdid_sdk_core::response_parser::SsdidResponse;

    let response = SsdidResponse::parse_vp_response(&body).map_err(FfiSdkError::from)?;
    Ok(FfiVpResponse {
        vp_token: response.vp_token,
        presentation_submission: response.presentation_submission,
        nonce: response.nonce,
    })
}

#[uniffi::export]
pub fn generate_qr_code(data: String, size: u32) -> Result<Vec<u8>, FfiSdkError> {
    ssdid_sdk_qr::generate_qr_png(&data, size).map_err(FfiSdkError::from)
}

// --- Data types ---

#[derive(uniffi::Record)]
pub struct FfiClaimEntry {
    pub name: String,
    pub priority: FfiClaimPriority,
}

#[derive(uniffi::Record)]
pub struct FfiKeyValue {
    pub key: String,
    pub value: String,
}

#[derive(uniffi::Record)]
pub struct FfiVpResponse {
    pub vp_token: String,
    pub presentation_submission: Option<String>,
    pub nonce: Option<String>,
}
```

**Step 2: Create uniffi-bindgen binary**

```rust
// crates/ssdid-sdk-ffi/uniffi-bindgen.rs
fn main() {
    uniffi::uniffi_bindgen_main()
}
```

Add to `crates/ssdid-sdk-ffi/Cargo.toml`:
```toml
[[bin]]
name = "uniffi-bindgen"
path = "uniffi-bindgen.rs"
```

**Step 3: Verify it compiles**

Run: `cargo build -p ssdid-sdk-ffi`
Expected: BUILD SUCCESS

**Step 4: Generate Kotlin and Swift bindings**

```bash
# Build the library first
cargo build --release -p ssdid-sdk-ffi

# Generate Kotlin
cargo run --bin uniffi-bindgen generate --library target/release/libssdid_sdk_ffi.dylib --language kotlin --out-dir bindings/kotlin

# Generate Swift
cargo run --bin uniffi-bindgen generate --library target/release/libssdid_sdk_ffi.dylib --language swift --out-dir bindings/swift
```

**Step 5: Verify bindings generated**

Run: `ls bindings/kotlin/ && ls bindings/swift/`
Expected: `.kt` file(s) in kotlin/, `.swift` + `.h` + `.modulemap` in swift/

**Step 6: Commit**

```bash
git add crates/ssdid-sdk-ffi/ bindings/
git commit -m "feat(ffi): add UniFFI bindings layer with Kotlin and Swift generation"
```

---

## Task 11: Integration Tests

**Files:**
- Create: `crates/ssdid-sdk-core/tests/integration_test.rs`

**Step 1: Write integration tests**

```rust
// crates/ssdid-sdk-core/tests/integration_test.rs
use ssdid_sdk_core::credential_request::SsdidCredentialRequest;
use ssdid_sdk_core::auth_request::SsdidAuthRequest;
use ssdid_sdk_core::credential_offer::SsdidCredentialOffer;
use ssdid_sdk_core::callback::SsdidCallback;
use ssdid_sdk_core::response_parser::SsdidResponse;
use ssdid_sdk_core::dcql::{CredentialFormat, ClaimPriority};

#[test]
fn full_credential_request_flow() {
    // 1. App builds a credential request
    let request = SsdidCredentialRequest::builder()
        .credential("IdentityCredential", CredentialFormat::SdJwt)
            .claim("name", ClaimPriority::Required)
            .claim("email", ClaimPriority::Optional)
            .done()
        .response_url("https://myapp.com/api/ssdid/response")
        .callback_scheme("myapp://ssdid/callback")
        .extra("tenant_id", "org-123")
        .build()
        .unwrap();

    let deeplink = request.to_deeplink();

    // 2. Verify deeplink is well-formed
    let url = url::Url::parse(&deeplink).unwrap();
    assert_eq!(url.scheme(), "ssdid");
    assert_eq!(url.host_str(), Some("authorize"));

    let params: std::collections::HashMap<_, _> = url.query_pairs().collect();
    assert!(params.contains_key("dcql_query"));
    assert!(params.contains_key("response_url"));
    assert!(params.contains_key("callback_scheme"));
    assert!(params.contains_key("nonce"));
    assert_eq!(params.get("tenant_id").unwrap().as_ref(), "org-123");

    // 3. Verify DCQL query is valid JSON with correct structure
    let dcql: serde_json::Value = serde_json::from_str(params.get("dcql_query").unwrap()).unwrap();
    assert_eq!(dcql["credentials"][0]["format"], "vc+sd-jwt");
    assert_eq!(dcql["credentials"][0]["meta"]["vct_values"][0], "IdentityCredential");
    assert_eq!(dcql["credentials"][0]["claims"][0]["path"][0], "name");
    assert_eq!(dcql["credentials"][0]["claims"][1]["optional"], true);

    // 4. QR string matches deeplink
    assert_eq!(request.to_qr_string(), deeplink);
}

#[test]
fn full_auth_flow() {
    let auth = SsdidAuthRequest::builder()
        .challenge("server-challenge-xyz")
        .accepted_algorithms(&["Ed25519", "EcdsaP256"])
        .response_url("https://myapp.com/api/auth/response")
        .callback_scheme("myapp://auth/callback")
        .build()
        .unwrap();

    let deeplink = auth.to_deeplink();
    let url = url::Url::parse(&deeplink).unwrap();
    assert_eq!(url.scheme(), "ssdid");
    assert_eq!(url.host_str(), Some("authenticate"));
}

#[test]
fn full_issuance_flow() {
    let offer = SsdidCredentialOffer::builder()
        .issuer_url("https://myapp.com")
        .credential_type("EmployeeBadge", CredentialFormat::SdJwt)
        .pre_authorized_code("pre-code-abc")
        .callback_scheme("myapp://issuance/callback")
        .build()
        .unwrap();

    let deeplink = offer.to_deeplink();
    assert!(deeplink.starts_with("openid-credential-offer://"));
}

#[test]
fn callback_round_trip() {
    // Success
    let success = SsdidCallback::parse("myapp://cb?status=success&response_id=r-1").unwrap();
    assert!(matches!(success, SsdidCallback::Success { response_id } if response_id == "r-1"));

    // Denied
    let denied = SsdidCallback::parse("myapp://cb?status=denied").unwrap();
    assert!(matches!(denied, SsdidCallback::Denied));

    // Error
    let error = SsdidCallback::parse("myapp://cb?status=error&error=no_matching_credentials").unwrap();
    assert!(matches!(error, SsdidCallback::Error { code } if code == "no_matching_credentials"));
}

#[test]
fn response_parser_form_encoded() {
    let body = "vp_token=test-token&presentation_submission=%7B%22id%22%3A%22ps-1%22%7D&nonce=n-1";
    let response = SsdidResponse::parse_vp_response(body).unwrap();
    assert_eq!(response.vp_token, "test-token");
    assert_eq!(response.nonce, Some("n-1".into()));
}

#[test]
fn mdoc_credential_request() {
    let request = SsdidCredentialRequest::builder()
        .credential("org.iso.18013.5.1.mDL", CredentialFormat::MsoMdoc)
            .claim("org.iso.18013.5.1/family_name", ClaimPriority::Required)
            .claim("org.iso.18013.5.1/birth_date", ClaimPriority::Optional)
            .done()
        .response_url("https://myapp.com/api/ssdid/response")
        .callback_scheme("myapp://ssdid/callback")
        .build()
        .unwrap();

    let deeplink = request.to_deeplink();
    let url = url::Url::parse(&deeplink).unwrap();
    let params: std::collections::HashMap<_, _> = url.query_pairs().collect();
    let dcql: serde_json::Value = serde_json::from_str(params.get("dcql_query").unwrap()).unwrap();

    assert_eq!(dcql["credentials"][0]["format"], "mso_mdoc");
    assert_eq!(dcql["credentials"][0]["meta"]["doctype_value"], "org.iso.18013.5.1.mDL");
    assert_eq!(dcql["credentials"][0]["claims"][0]["namespace"], "org.iso.18013.5.1");
    assert_eq!(dcql["credentials"][0]["claims"][0]["claim_name"], "family_name");
}
```

**Step 2: Run integration tests**

Run: `cargo test -p ssdid-sdk-core --test integration_test`
Expected: PASS (6 tests)

**Step 3: Commit**

```bash
git add crates/ssdid-sdk-core/tests/
git commit -m "test: add integration tests for full request/callback/response flows"
```

---

## Task 12: Wallet-Side Changes (ssdid-wallet)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/presentation/PresentationRequestViewModel.kt`
- Test: existing test files + new tests

**Step 1: Add `authorize` action to DeepLinkHandler**

Add a new `Authorize` case to `DeepLinkAction` that extracts `dcql_query`, `response_url`, `callback_scheme`, and `nonce` from the deeplink. This action should route to the existing presentation request screen, converting the DCQL query into the format `OpenId4VpHandler` already accepts.

**Step 2: Fix decline to post error and redirect**

In `PresentationRequestViewModel.decline()`, add:
1. Call `transport.postError(responseUri, "access_denied", state)`
2. Build callback URI: `{callback_scheme}?status=denied`
3. Return the callback URI so the UI can redirect

**Step 3: Add success redirect after VP submission**

After `submitPresentation()` succeeds and direct_post returns `response_id`, build callback URI: `{callback_scheme}?response_id={id}&status=success` and redirect.

**Step 4: Test the new deeplink action**

Add tests for parsing `ssdid://authorize?dcql_query=...&response_url=...&callback_scheme=...&nonce=...`

**Step 5: Test decline posts error**

Verify `transport.postError()` is called when user declines.

**Step 6: Commit**

```bash
git add app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt
git add app/src/main/java/my/ssdid/wallet/feature/presentation/PresentationRequestViewModel.kt
git commit -m "feat(wallet): add ssdid://authorize deeplink action and decline error posting"
```

---

## Summary

| Task | Component | Tests |
|------|-----------|-------|
| 1 | Scaffold workspace | compile check |
| 2 | Error types | 4 unit |
| 3 | DCQL builder | 4 unit |
| 4 | Credential request builder | 7 unit |
| 5 | Auth request builder | 4 unit |
| 6 | Credential offer builder | 4 unit |
| 7 | Callback parser | 6 unit |
| 8 | Response parser | 3 unit |
| 9 | QR generator | 3 unit |
| 10 | UniFFI bindings | compile + generate |
| 11 | Integration tests | 6 integration |
| 12 | Wallet-side changes | 4+ unit |

**Total: ~45 tests across 12 tasks**

Dependencies: Task 1 → 2 → 3 → 4,5,6 (parallel) → 7,8 (parallel) → 9 → 10 → 11 → 12
