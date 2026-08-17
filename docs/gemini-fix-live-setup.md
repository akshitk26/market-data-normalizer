# Gemini FIX Live Session Setup

The parser and file processor are ready. A live Gemini FIX session needs an exchange-provisioned session before the application can connect.

## What Gemini access is for

This is market-data access only. The application needs to establish a FIX session, subscribe with a `35=V` Market Data Request, receive a `35=W` snapshot, then consume `35=X` incremental updates.

Gemini's current Logon documentation says it authenticates the institution using the source IP, `49=SenderCompID`, and `56=TargetCompID`. It documents `98=0` for no FIX-level encryption and requires `108=30` for the heartbeat interval. The transport host and port are provisioned by Gemini rather than guessed by this project.

Use the [onboarding request template](gemini-fix-onboarding-request.md) when contacting Gemini.

## What you must obtain from Gemini

1. Request Gemini FIX market-data access for the institution or account.
2. Ask for a non-production or certification session first, if Gemini offers one.
3. Provide the machine's fixed public egress IP for allowlisting. A changing home IP may not work; a fixed-IP host or approved VPN may be required.
4. Obtain the FIX host, port, transport requirements, session schedule, and any TLS/certificate instructions.
5. Obtain the assigned `SenderCompID` and confirm the server `TargetCompID`.
6. Confirm the supported symbols, top-of-book/full-book depth, and requested entry types.
7. Ask how Gemini expects sequence numbers to be initialized, persisted, reset, and recovered after disconnect.
8. Confirm that the permission is market-data-only and does not enable order entry.

Do not send API secrets, private certificates, or account credentials in chat. The assistant cannot create the Gemini account, accept agreements, provide an allowlisted IP, or obtain the private endpoint on your behalf.

## Local preparation

Copy `config/gemini-fix.env.example` to a local ignored file and replace the placeholder values with the values Gemini gives you. Keep the local file outside version control. No API key should be added unless Gemini explicitly documents one for the FIX session; the current Logon fields are session identifiers and sequence/session controls rather than a REST HMAC signature.

Before the live adapter is enabled, verify the network path with the exact host and port Gemini provides:

```bash
nc -vz "$GEMINI_FIX_HOST" "$GEMINI_FIX_PORT"
```

## What the live adapter will do

The implemented Java FIX initiator:

- opens the provisioned TCP/TLS connection;
- sends `35=A` Logon with `34`, `49`, `56`, `98=0`, and `108=30`;
- handles Logon, Heartbeat, Test Request, Logout, sequence warnings, and Resend Requests;
- sends a market-data `35=V` request for configured symbols;
- frames messages using `8`, `9`, and `10` correctly;
- writes every received raw FIX message to JSONL;
- passes `35=W` and `35=X` messages to the existing parser;
- leaves replay-buffer integration disabled until all three source families are integrated.

The adapter should not implement order entry or use `35=D` New Order Single messages.

## What to send back after onboarding

Once Gemini provides access, record these locally:

- host and port;
- whether transport TLS or a private network is required;
- sender and target CompIDs;
- assigned source IP or VPN requirement;
- sequence-number rules;
- certification/sandbox instructions.

Do not paste secrets or certificates here. With those non-secret connection details available locally, the live adapter can be implemented and tested against Gemini's session.
