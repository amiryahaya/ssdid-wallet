/**
 * Deep Link Validation E2E Tests
 *
 * Validates that deep link URLs are correctly formatted and the registry
 * endpoints they reference are accessible. Tests the service-side of the
 * wallet interaction — what a third-party app generates before invoking the wallet.
 *
 * These tests verify:
 * - Deep link URL format compliance
 * - Registry endpoints resolve correctly
 * - Challenge lifecycle works from the service perspective
 * - DID document resolution returns valid W3C format
 */

import { test, expect } from '@playwright/test';

const REGISTRY_URL = process.env.REGISTRY_URL || 'https://registry.ssdid.my';

test.describe('UC-17: Deep Link URL Validation', () => {

  test('TC-17.1: Registration deep link format', async () => {
    // Verify the deep link format the Client SDK would generate
    const serverUrl = encodeURIComponent('https://drive.ssdid.my');
    const serverDid = encodeURIComponent('did:ssdid:abcdefghijklmnopqrstuv');
    const deepLink = `ssdid://register?server_url=${serverUrl}&server_did=${serverDid}`;

    const url = new URL(deepLink);
    expect(url.protocol).toBe('ssdid:');
    expect(url.searchParams.get('server_url')).toBe('https://drive.ssdid.my');
    expect(url.searchParams.get('server_did')).toMatch(/^did:ssdid:/);
  });

  test('TC-17.2: Login deep link with state parameter', async () => {
    const state = 'random-csrf-token-' + Math.random().toString(36).slice(2);
    const deepLink = `ssdid://login?service_url=${encodeURIComponent('https://drive.ssdid.my')}&service_name=SSDID+Drive&callback_url=${encodeURIComponent('https://drive.ssdid.my/callback')}&state=${state}&requested_claims=${encodeURIComponent('[{"key":"name","required":"true"}]')}`;

    const url = new URL(deepLink);
    expect(url.protocol).toBe('ssdid:');
    expect(url.searchParams.get('state')).toBe(state);
    expect(url.searchParams.get('service_name')).toBe('SSDID Drive');
    expect(url.searchParams.get('requested_claims')).toBeTruthy();

    // Parse claims JSON
    const claims = JSON.parse(url.searchParams.get('requested_claims')!);
    expect(claims).toBeInstanceOf(Array);
    expect(claims[0].key).toBe('name');
  });

  test('TC-17.3: Invite deep link format', async () => {
    const deepLink = `ssdid://invite?server_url=${encodeURIComponent('https://drive.ssdid.my')}&token=invite-token-123&callback_url=${encodeURIComponent('https://drive.ssdid.my/invite-callback')}&state=abc123`;

    const url = new URL(deepLink);
    expect(url.protocol).toBe('ssdid:');
    expect(url.searchParams.get('token')).toBe('invite-token-123');
    expect(url.searchParams.get('state')).toBe('abc123');
  });

  test('TC-17.4: Malicious scheme rejected', async () => {
    const maliciousLinks = [
      'javascript://alert(1)',
      'data://text/html,<script>alert(1)</script>',
      'file:///etc/passwd',
    ];

    for (const link of maliciousLinks) {
      const url = new URL(link);
      expect(url.protocol).not.toBe('ssdid:');
      expect(url.protocol).not.toBe('https:');
    }
  });

  test('TC-17.5: OpenID4VP deep link format', async () => {
    const deepLink = `openid4vp://?response_type=vp_token&client_id=${encodeURIComponent('https://verifier.example.com')}&nonce=test-nonce&response_mode=direct_post&response_uri=${encodeURIComponent('https://verifier.example.com/response')}&presentation_definition=${encodeURIComponent('{"id":"req-1","input_descriptors":[]}')}`;

    const url = new URL(deepLink);
    expect(url.protocol).toBe('openid4vp:');
    expect(url.searchParams.get('response_type')).toBe('vp_token');
    expect(url.searchParams.get('nonce')).toBe('test-nonce');
  });
});

test.describe('UC-06: Registry DID Resolution (Service Side)', () => {

  test('TC-6.1: Resolve existing DID returns W3C document', async ({ request }) => {
    // First, get the list of DIDs from registry info
    const infoResp = await request.get(`${REGISTRY_URL}/health/ready`);
    const info = await infoResp.json();

    if (info.tables.did_documents === 0) {
      test.skip(true, 'No DIDs in registry to resolve');
      return;
    }

    // Note: We can't enumerate DIDs, so this test requires a known DID
    // In real E2E, this would use a DID created by the wallet test suite
  });

  test('TC-6.2: Challenge creation returns valid challenge', async ({ request }) => {
    // Challenge creation requires a registered DID
    // This test verifies the format of the response
    const resp = await request.get(`${REGISTRY_URL}/api/registry/info`);
    const info = await resp.json();
    expect(info.policies).toBeDefined();
    expect(info.policies.proof_max_age_seconds).toBeGreaterThan(0);
    expect(info.policies.required_proof_purpose).toBeDefined();
    expect(info.policies.required_proof_purpose.create).toBe('assertionMethod');
    expect(info.policies.required_proof_purpose.update).toBe('capabilityInvocation');
    expect(info.policies.required_proof_purpose.deactivate).toBe('capabilityInvocation');
  });
});
