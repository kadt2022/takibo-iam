# Guide de test ADP v1

## Démarrage

```bash
cd takibo-adp-test
../gradlew bootRun
```

L'application démarre sur **http://localhost:8080**

## Utilisateurs de test

| Username   | Password   | Roles      | Profil                |
|------------|------------|------------|-----------------------|
| user       | password   | USER       | Normal (50 accès)     |
| admin      | admin      | USER,ADMIN | Admin (100 accès)     |
| suspicious | suspicious | USER       | Suspect (2 accès)     |

## Scénarios de test

### 1. Health check (public)

```bash
curl http://localhost:8080/api/health
```

**Attendu** : `200 OK` (pas d'auth requise)

---

### 2. Accès normal (user connu)

```bash
curl -u user:password http://localhost:8080/api/dashboard
```

**Attendu** :
- `200 OK` 
- ADP : `ALLOW` (device connu, profil sain)
- Risk score : ~20-30
- Logs : "Known device (seen 50x, last...)"

---

### 3. Accès admin resource (user normal)

```bash
curl -u user:password http://localhost:8080/api/admin/users
```

**Attendu** :
- `403 FORBIDDEN` ou `200` selon threshold adaptatif
- ADP : `CHALLENGE` ou `DENY`
- Risk score augmenté (admin resource)
- Thresholds ajustés : deny=55, challenge=30

---

### 4. Accès admin resource (admin user)

```bash
curl -u admin:admin http://localhost:8080/api/admin/users
```

**Attendu** :
- `200 OK`
- ADP : `ALLOW` (admin role + device très connu)
- Risk score faible
- Confidence haute

---

### 5. Utilisateur suspect (nouveau device)

```bash
curl -u suspicious:suspicious http://localhost:8080/api/dashboard
```

**Attendu** :
- `403 FORBIDDEN` ou `CHALLENGE`
- ADP : `CHALLENGE` ou `DENY`
- Risk score : ~70-80
- Raison : "New device fingerprint detected" ou "No fingerprint history"

---

### 6. Test velocity (burst)

Lancer 20 requêtes en rafale :

```bash
for i in {1..20}; do
  curl -u user:password http://localhost:8080/api/dashboard &
done
wait
```

**Attendu** :
- Premières requêtes : `200 OK`
- Après 10-15 requêtes : ADP commence à `CHALLENGE`
- Risk score augmente (z-score > 2)
- Logs : "Velocity: current=X rpm, baseline=5.0±2.0, z-score=Y"

---

### 7. Test avec proxy header

```bash
curl -u user:password \
  -H "Via: 1.1 proxy.example.com" \
  http://localhost:8080/api/dashboard
```

**Attendu** :
- ADP : `CHALLENGE` ou `DENY`
- Risk score augmenté
- Logs : "High risk network detected: Proxy"

---

### 8. Désactiver ADP (test fallback)

Modifier `application.yml` :
```yaml
takibo:
  adp:
    enabled: false
```

Redémarrer et tester :

```bash
curl -u user:password http://localhost:8080/api/dashboard
```

**Attendu** :
- `200 OK`
- ADP : `ALLOW` (neutral decision)
- Logs : "ADP is disabled"

---

## Endpoints de test

| Endpoint               | Auth    | ADP Evaluation              |
|------------------------|---------|-----------------------------|
| `/api/health`          | Public  | Non                         |
| `/api/public/info`     | Public  | Non                         |
| `/api/dashboard`       | USER    | Oui (threshold baseline)    |
| `/api/profile`         | USER    | Oui (threshold baseline)    |
| `/api/admin/users`     | ANY     | Oui (threshold strict)      |
| `/api/data/sensitive`  | USER    | Oui (threshold baseline)    |

---

## Observation des logs

### Logs ADP décision

```
ADP Decision: user=user path=/api/dashboard decision=ALLOW risk=24.3 confidence=0.78 
explanation=Risk score: 24.3/100 (confidence: 0.78). Thresholds: deny=75, challenge=40 (baseline). 
Main factors: DeviceBaselineEvaluator (25): Known device (seen 50x, last 1h ago);
```

### Logs agrégation

```
Aggregation: score=24.3 confidence=0.78 health=1.00
```

### Logs evaluators

```
DeviceBaselineEvaluator: Known device (seen 50x, last 1h ago)
VelocityAnomalyEvaluator: Velocity: current=2.0 rpm, baseline=5.0±2.0, z-score=-1.50
NetworkRiskEvaluator: Network appears normal
TimeRiskEvaluator: Access during typical business hours
LocationRiskEvaluator: Location consistent with user profile
```

---

## Vérification des features

### Baseline device trust

```bash
# Premier accès (device inconnu)
curl -u newuser:password http://localhost:8080/api/dashboard

# Logs attendus : "New device fingerprint detected", risk ~75
```

### Adaptive thresholds

```bash
# Resource normale
curl -u user:password http://localhost:8080/api/dashboard
# → deny=75, challenge=40

# Resource admin
curl -u user:password http://localhost:8080/api/admin/users
# → deny=55, challenge=30 (ajusté)
```

### Confidence propagation

Si plusieurs evaluators timeout (simuler avec timeout très court) :
- Confidence baisse
- Si confidence < 0.55 → CHALLENGE automatique

---

## Tests d'intégration

```bash
# Test complet : user normal
./gradlew :takibo-adp-test:test --tests TestSecurityIntegrationTest.testNormalUserAccess

# Test admin resource
./gradlew :takibo-adp-test:test --tests TestSecurityIntegrationTest.testAdminResourceAccess
```

---

## Troubleshooting

### Problème : 401 Unauthorized

**Cause** : Mauvais credentials
**Solution** : Vérifier username:password (user:password, admin:admin, suspicious:suspicious)

### Problème : Tous les accès refusés

**Cause** : ADP trop strict ou configuration invalide
**Solution** : 
1. Vérifier logs ADP
2. Augmenter thresholds dans `DefaultThresholdPolicy`
3. Désactiver temporairement avec `takibo.adp.enabled=false`

### Problème : Pas de logs ADP

**Cause** : Logging level trop élevé
**Solution** : Dans `application.yml` :
```yaml
logging:
  level:
    com.takibo.adp: DEBUG
```

---

## Prochaines étapes

1. **Profil JPA** : Remplacer `TestBehaviorProfileReader` par `JpaBehaviorProfileReader`
2. **Writer async** : Implémenter `AsyncJpaBehaviorProfileWriter`
3. **Tests de charge** : Vérifier performance sous charge (JMeter/Gatling)
4. **Métriques** : Ajouter Micrometer pour observer latence P95/P99
