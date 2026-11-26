# Identitatea de semnare Android

## Pe scurt

Înregistrarea din proiect a fost cerută explicit pentru Chess. Conține cheia
PKCS12 criptată, certificatul public, un manifest cu căi relative și parola
protejată prin Windows DPAPI. Parola în clar nu se păstrează în repository.

- Încărcați cu `. ./release-tools/scripts/Initialize-J7Signing.ps1 -ProjectIdentity`, în
  același cont Windows care a protejat `password.dpapi`.
  `-DataRoot` explicit selectează înregistrarea externă în locul detectării automate.
- `-StartNewIdentity` este necesar numai pentru inițializarea unei identități
  lipsă după un certificat publicat. Înregistrările existente sau parțiale nu se suprascriu.
- `identity.json` păstrează certificatul publicat anterior. Acea tranziție istorică
  a cerut instalare curată; actualizările curente se verifică față de semnatarul ediției publicate curente.
- Protecția DPAPI aparține acestui cont Windows. Clonarea pe alt cont nu oferă
  o parolă utilizabilă. Păstrați separat materialul de recuperare al contului;
  nu transformați parola într-un fișier text comis.
- Păstrați logurile, APK-urile și rapoartele de calificare în afara directorului.
  Logurile semnării folosesc `-WorkRoot` extern, sub `C:\work\sah`.
  [Protocolul ediției](../../docs/release/README.md#calificare-și-publicare).
