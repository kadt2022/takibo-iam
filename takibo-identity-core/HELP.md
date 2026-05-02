# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.5/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.5/gradle-plugin/packaging-oci-image.html)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)


1. Les acteurs principaux

User = un utilisateur.

Role = un rôle (ex: ADMIN, USER).

Permission = une action précise (ex: USER_READ, USER_WRITE).

Group = un regroupement d’utilisateurs (ex: “Équipe Finance”).

2. Les liaisons

User ↔ Role :
→ Un utilisateur peut avoir plusieurs rôles, et un rôle peut être donné à plusieurs utilisateurs.
(Table user_roles fait le lien).

Group ↔ User :
→ Un utilisateur peut être dans plusieurs groupes, un groupe peut contenir plusieurs utilisateurs.
(Table group_members).

Group ↔ Role :
→ Un groupe peut recevoir un rôle. Tous ses membres héritent de ce rôle.
(Table group_roles).

Role ↔ Permission :
→ Un rôle donne accès à plusieurs permissions.
(Table role_permissions).

User ↔ Permission :
→ Permet d’ajouter ou retirer directement une permission à un utilisateur (en plus des rôles/groupes).
(Table user_permissions).

3. Les extras

UserIdentity : lien avec un compte externe (Google, AzureAD, etc.).

MfaPolicy : règles MFA d’un espace (ex: MFA obligatoire).

UserMfaFactor : facteurs MFA d’un user (TOTP, WebAuthn, SMS…).

UserRecoveryCode : codes de secours d’un user pour se connecter s’il perd son MFA.

 En gros :

User reçoit des Roles (directement ou via Groups).

Les Roles apportent des Permissions.

On peut aussi donner/retirer des Permissions directement à un User.
