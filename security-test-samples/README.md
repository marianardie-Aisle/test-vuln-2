# Security Test Samples

**FOR TESTING PURPOSES ONLY**

This folder contains synthetic code snippets that **simulate** security vulnerabilities. They are:

- **Non-exploitable** – No real credentials, databases, or system calls
- **Safe** – Return strings or use placeholder data only
- **Intended for** – SAST/DAST tool validation, security training, code review practice

## Contents

| File | Language | Simulated issues |
|------|----------|------------------|
| `vulnerability-examples.js` | JavaScript | SQL injection, XSS, command injection, path traversal, hardcoded secrets, weak random, sensitive logging |
| `vulnerability-examples.py` | Python | SQL injection, hardcoded secret, command injection, weak hash (MD5), sensitive print, assert in logic |
| `vulnerability-examples.html` | HTML/JS | Reflected XSS pattern, document.write pattern |

## Criticality (for reference)

- **Critical:** SQL injection, hardcoded credentials
- **High:** XSS, command injection
- **Medium:** Path traversal, weak crypto, insecure deserialization
- **Low:** Weak random, verbose logging, debug patterns

**Do not use this code in production. Do not add real secrets or connect to real systems.**
