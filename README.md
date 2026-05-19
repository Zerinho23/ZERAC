# ⚡ ZERAC AntiCheat

<div align="center">

![ZERAC Banner](https://img.shields.io/badge/ZERAC-AntiCheat-6C63FF?style=for-the-badge&logo=minecraft&logoColor=white)
![Version](https://img.shields.io/badge/version-1.0.0-A78BFA?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Paper](https://img.shields.io/badge/Paper%2FSpigot-1.8--1.21-green?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)

**Plugin AntiCheat premium para Paper/Spigot con Blacklist Global, detección avanzada de hacks y AntiVPN.**

[Características](#-características) · [Instalación](#-instalación) · [Configuración](#-configuración) · [Comandos](#-comandos) · [API](#-api-para-desarrolladores)

</div>

---

## 📖 Descripción

ZERAC es un AntiCheat moderno, altamente configurable y de alto rendimiento desarrollado en **Java 21** con **PacketEvents** y **Adventure API**. Diseñado para competir con soluciones premium como Grim, Vulcan e Intave.

### Por qué ZERAC?

| Característica | ZERAC | Soluciones básicas |
|---|---|---|
| Blacklist Global Centralizada | ✅ | ❌ |
| AntiVPN con caché de IPs | ✅ | ❌ |
| Sistema de VL con decay | ✅ | Parcial |
| PacketEvents 2.x | ✅ | ❌ |
| Adventure API (MiniMessage) | ✅ | ❌ |
| HikariCP connection pool | ✅ | ❌ |
| Discord Webhooks | ✅ | ❌ |
| Arquitectura modular | ✅ | ❌ |
| 0 operaciones en hilo principal | ✅ | ❌ |

---

## ✨ Características

### 🎯 Checks Implementados

#### Combat
| Check | Descripción | Método de detección |
|---|---|---|
| **KillAura** | Ataques automáticos a múltiples entidades | Análisis de ángulos + ataques por tick |
| **Reach** | Distancia de ataque superior a la legítima | Medición precisa de distancia eye-to-hitbox |
| **AimAssist** | Asistencia de apuntado inhumana | Análisis GCD de deltas de rotación |
| **AutoClicker** | Clic automático / macros | CPS + análisis de consistencia de intervalos |

#### Movement
| Check | Descripción | Método de detección |
|---|---|---|
| **Speed** | Movimiento horizontal excesivo | Speed multiplier vs. estado actual |
| **Fly** | Vuelo/flotación ilegal | Airtime ticks + análisis de gravedad |
| **Velocity** | Ignorar knockback del servidor | Ratio de velocidad aceptada post-golpe |
| **Timer** | Aceleración del reloj del cliente | Ratio de paquetes de posición por segundo |
| **NoSlow** | Ignorar penalización de ítem en uso | Speed real vs. esperada durante uso |

#### World
| Check | Descripción | Método de detección |
|---|---|---|
| **Scaffold** | Construcción automática de puentes | Placements/segundo + rotación divergente |
| **FastBreak** | Romper bloques más rápido de lo posible | Tiempo real vs. tiempo esperado por tipo |
| **FastPlace** | Colocación de bloques ultrarrápida | Delay mínimo entre colocaciones consecutivas |

### 🌐 Blacklist Global

Sistema centralizado de bans sincronizado entre todos los servidores que usan ZERAC:

```
POST /ban          — Añadir UUID a la blacklist
GET  /check/{uuid} — Consultar si un UUID está baneado  
POST /unban        — Eliminar UUID de la blacklist
```

- Caché local para lookups instantáneos sin latencia de API
- Sincronización periódica configurable (por defecto cada 5 minutos)
- Autenticación con `X-ZERAC-Token` header
- Bloqueo automático al entrar al servidor

### 🛡️ AntiVPN

Detecta VPNs, proxies y datacenters:
- **Proveedores**: proxycheck.io o IPQualityScore
- Caché de IPs con TTL configurable
- Acciones configurables: `KICK`, `BAN`, o `LOG`
- Whitelist de IPs y países
- Logs en base de datos

### 🔔 Sistema de Alertas

```
[ZERAC] Kevin detectado usando Speed (x6) — VL: 60/100
```

Al pasar el cursor sobre la alerta:
```
Jugador: Kevin
Check: Speed
VL actual: 60/100
Ping: 43ms
TPS: 19.8
Versión: 1.20.4
Coords: 150, 64, -200
Servidor: BoxPvP-01
```

Click en la alerta → teleportación directa al jugador.

### ⚖️ Sistema de Punishments

Auto-detección del plugin de bans instalado:
1. **LiteBans** (prioridad máxima)
2. **AdvancedBan** (fallback)
3. **Vanilla Bukkit BanList** (fallback final)

Umbrales de VL configurables:
```yaml
vl-thresholds:
  warn: 50
  kick: 75
  ban: 100
  global-ban: 150
```

### 📊 Base de Datos

Soporte completo para SQLite y MySQL con HikariCP:

| Tabla | Descripción |
|---|---|
| `zerac_players` | Registro de jugadores (UUID, nombre, fechas) |
| `zerac_violations` | Historial de flags individuales |
| `zerac_bans` | Historial de bans locales y globales |
| `zerac_vpn_logs` | Logs de detecciones VPN |
| `zerac_alerts` | Historial de alertas enviadas a staff |

### 🎮 GUI de Administración

Inventario interactivo con:
- **Panel principal** — navegación + TPS en tiempo real
- **Jugadores** — lista online con flags y ping
- **Checks** — estado de cada check (activo/deshabilitado)
- **Estadísticas** — contadores globales de detecciones y bans
- **Blacklist** — entradas de la blacklist global en caché
- **Punishments** — historial de bans

---

## 📦 Instalación

### Requisitos

- **Java 21+**
- **Paper** o **Spigot** 1.8 – 1.21
- **PacketEvents** 2.5.0+ (como dependency plugin)

### Pasos

1. Descarga `ZERAC-1.0.0.jar` de la sección [Releases](../../releases)
2. Coloca el archivo en la carpeta `plugins/` del servidor
3. Asegúrate de tener **PacketEvents** instalado también
4. Inicia el servidor — ZERAC generará `config.yml` y `messages.yml`
5. Configura los valores en `plugins/ZERAC/config.yml`
6. Ejecuta `/zerac reload` para aplicar cambios sin reiniciar

### Soft Dependencies (opcionales)

| Plugin | Función |
|---|---|
| LiteBans | Sistema de bans preferente |
| AdvancedBan | Alternativa a LiteBans |
| ViaVersion | Detección de versión del cliente |
| Geyser / Floodgate | Exención de jugadores Bedrock |

---

## ⚙️ Configuración

### config.yml — Fragmento principal

```yaml
plugin:
  server-name: "BoxPvP-01"
  license-key: "TU_LICENSE_KEY"

database:
  type: MYSQL  # SQLITE o MYSQL
  mysql:
    host: "localhost"
    port: 3306
    database: "zerac"
    username: "root"
    password: ""

anti-vpn:
  enabled: true
  provider: "proxycheck"   # proxycheck o ipqualityscore
  api-key: "TU_API_KEY"
  action: KICK             # KICK, BAN, o LOG

blacklist:
  enabled: true
  api-url: "https://api.zerac.gg"
  api-token: "TU_API_TOKEN"
  sync-interval: 5         # minutos

punishments:
  provider: AUTO           # AUTO, LITEBANS, ADVANCEDBAN, VANILLA
  vl-thresholds:
    warn: 50
    kick: 75
    ban: 100
    global-ban: 150

webhooks:
  detection:
    enabled: true
    url: "https://discord.com/api/webhooks/..."
    min-vl: 50
  ban:
    enabled: true
    url: "https://discord.com/api/webhooks/..."
```

### Configuración de un check

```yaml
checks:
  KillAura:
    enabled: true
    experimental: false
    vl-increment: 15       # VL añadido por flag
    vl-decay: 1            # VL reducido por tick limpio
    max-vl: 100            # VL máximo antes de punishment
    setback: true          # Teleportar al jugador al último punto seguro
    punishment: BAN        # Acción al alcanzar max-vl
    debug: false           # Log en consola de cada flag
    max-angle-diff: 30.0   # Tolerancia de ángulo (grados)
    max-attacks-per-tick: 3
```

---

## 🔧 Comandos

| Comando | Permiso | Descripción |
|---|---|---|
| `/zerac alerts` | `zerac.alerts` | Toggle feed de alertas personales |
| `/zerac debug <check>` | `zerac.debug` | Activar debug de un check específico |
| `/zerac gui` | `zerac.gui` | Abrir panel de administración |
| `/zerac reload` | `zerac.reload` | Recargar configuración |
| `/zerac stats` | `zerac.stats` | Ver estadísticas globales |
| `/zerac blacklist add/remove/list` | `zerac.blacklist.manage` | Gestionar blacklist global |
| `/zerac check <player>` | `zerac.command` | Ver VL actual de un jugador |

### Permisos

```
zerac.admin          — Acceso completo
zerac.bypass         — Exento de todos los checks
zerac.bypass.combat  — Exento de checks de combate
zerac.bypass.movement — Exento de checks de movimiento
zerac.bypass.world   — Exento de checks de world
```

---

## 📡 Discord Webhooks

ZERAC envía embeds ricos a Discord para:

**Detection embed** (cuando un jugador es detectado):
```
⚠️ Detection — KillAura
Kevin triggered KillAura
Player: Kevin | Check: KillAura | VL: 60 / 100
Ping: 43ms | TPS: 19.8 | Server: BoxPvP-01
```

**Ban embed** (cuando un jugador es baneado):
```
🔨 Ban
Player: Kevin | UUID: ...
Reason: Hacking [KillAura] | Check: KillAura
Server: BoxPvP-01 | ID: #A91D2F | Global: No
```

**VPN embed** (cuando se detecta VPN):
```
🛡️ VPN Detected
Player: Kevin | IP: 192.168.x.x | Country: DE
Server: BoxPvP-01 | Action: KICK
```

---

## 🔌 API para Desarrolladores

```java
// Obtener la API
ZeracAPI api = ZeracPlugin.getInstance().getApi();

// Ver VL de un jugador en un check específico
double vl = api.getVL(player.getUniqueId(), "KillAura");

// Ver todos los VLs de un jugador
Map<String, Double> allVls = api.getAllVLs(player.getUniqueId());

// Exentar a un jugador temporalmente
api.setExempt(player.getUniqueId(), true);

// Consultar si un UUID está en la blacklist global
api.isBlacklisted(uuid).thenAccept(blacklisted -> {
    if (blacklisted) {
        // manejar...
    }
});

// Registrar un check personalizado
api.registerCheck(new MiCheckPersonalizado(plugin));

// Obtener historial de violaciones
api.getViolationHistory(uuid).thenAccept(violations -> {
    violations.forEach(v -> System.out.println(v.getCheckName() + ": " + v.getVl()));
});
```

---

## 🏗️ Arquitectura

```
me.zerinho23.zerac
│
├── ZeracPlugin.java           — Entry point + lifecycle
│
├── api/
│   └── ZeracAPI.java          — Public API para terceros
│
├── checks/
│   ├── AbstractCheck.java     — Base class con VL, decay, flag
│   ├── CheckRegistry.java     — Registro y dispatch de checks
│   ├── combat/
│   │   ├── KillAura.java
│   │   ├── Reach.java
│   │   ├── AimAssist.java
│   │   └── AutoClicker.java
│   ├── movement/
│   │   ├── Speed.java
│   │   ├── Fly.java
│   │   ├── Velocity.java
│   │   ├── Timer.java
│   │   └── NoSlow.java
│   └── world/
│       ├── Scaffold.java
│       ├── FastBreak.java
│       └── FastPlace.java
│
├── alerts/
│   └── AlertManager.java      — Broadcast MiniMessage + hover + click
│
├── commands/
│   └── ZeracCommand.java      — /zerac con tab-completion
│
├── config/
│   ├── ZeracConfig.java       — Config wrapper (thread-safe)
│   └── CheckConfig.java       — Typesafe check config section
│
├── data/
│   ├── cache/
│   │   └── PlayerDataCache.java  — In-memory UUID → PlayerData map
│   └── database/
│       └── DatabaseManager.java  — HikariCP + async SQL operations
│
├── gui/
│   └── GuiManager.java        — Inventory-based admin GUI
│
├── listeners/
│   ├── PacketListener.java    — PacketEvents interception
│   └── PlayerJoinLeaveListener.java
│
├── managers/
│   ├── AntiVPNManager.java    — proxycheck.io / IPQualityScore
│   ├── BlacklistManager.java  — REST API + local cache
│   ├── PunishmentManager.java — LiteBans → AdvancedBan → Vanilla
│   └── WebhookManager.java    — Discord embeds
│
├── models/
│   ├── PlayerData.java        — Runtime per-player state
│   ├── BanRecord.java         — Immutable ban DTO
│   └── ViolationRecord.java   — Immutable flag DTO
│
└── utils/
    ├── MessageUtil.java       — MiniMessage helpers
    ├── MathUtil.java          — Reach, GCD, velocity, angles
    └── TPSUtil.java           — TPS reading (Paper API + NMS fallback)
```

---

## 🔒 Seguridad

- **Protección de API** — Header `X-ZERAC-Token` en todas las peticiones a la blacklist
- **Sin inyección SQL** — PreparedStatement en todas las queries
- **Rate limiting** en alertas (cooldown configurable por tick)
- **Relocación de librerías** en el shade — no hay conflictos con otros plugins
- **Sin operaciones bloqueantes** en el hilo principal — todo I/O es async

---

## 📈 Rendimiento

- **PacketEvents 2.x** — overhead mínimo de packet interception
- **HikariCP** — pool de conexiones DB de alto rendimiento
- **ConcurrentHashMap** — estructuras thread-safe sin locks
- **CompletableFuture** — operaciones async sin bloquear hilos
- **Cache de IPs** para AntiVPN — reduce llamadas a API externa
- **Caché local de blacklist** — sin latencia de red en joins

---

## 🛠️ Compilar desde el código fuente

```bash
# Clonar el repositorio
git clone https://github.com/zerinho23/zerac.git
cd zerac

# Compilar con Maven
mvn clean package

# El jar estará en target/ZERAC-1.0.0.jar
```

**Requisitos para compilar:**
- JDK 21+
- Maven 3.8+

---

## 📝 Licencia

```
MIT License — Copyright (c) 2024 zerinho23

Se permite el uso, copia, modificación y distribución de este software
con o sin restricciones, siempre que se incluya este aviso de copyright.
```

---

## 💬 Soporte

- **Discord**: [discord.gg/zerac](https://discord.gg/zerac)
- **Issues**: [GitHub Issues](../../issues)
- **Wiki**: [GitHub Wiki](../../wiki)

---

<div align="center">

**Desarrollado con ❤️ por zerinho23**

*ZERAC AntiCheat — Protege tu servidor, elimina los tramposos.*

</div>
