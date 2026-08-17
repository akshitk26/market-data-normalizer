# Gemini FIX Market-Data Access Request

Use this as a starting point when contacting Gemini institutional/API support. Do not include passwords, private certificates, API secrets, or credentials in a public issue or repository.

## Request

Please provision a Gemini FIX market-data session for a development or certification environment, if available. The session should be market-data-only and support a subscription request followed by a snapshot and incremental updates.

## Information To Request From Gemini

- FIX host and port
- Certification or production environment
- Transport security requirements: plaintext TCP or TLS, including certificate requirements
- Source public IP address or VPN/network requirement for allowlisting
- Client `SenderCompID` (`49`)
- Gemini `TargetCompID` (`56`), normally `GEMINI`
- FIX version and session schedule/time zone
- Sequence-number initialization, persistence, reset, and reconnect rules
- Supported symbols
- Supported market depth: top-of-book or full book
- Supported market-data entry types: bid, offer, and trade
- Any connection timeout, rate limit, or certification test requirements

## Expected Session Behavior

The client will send:

- `35=A` Logon
- `98=0`
- `108=30`
- `35=V` Market Data Request with `263=1`

The client expects:

- `35=A` Logon acknowledgment
- `35=W` snapshot/full refresh
- `35=X` incremental refresh messages

The application does not send order-entry messages.

## Do Not Send Here

- API keys or API secrets
- Passwords
- Private keys or certificates
- Account credentials
- Production-only endpoint details in public issues

Store returned values only in a local ignored file such as `config/gemini-fix.env`.
