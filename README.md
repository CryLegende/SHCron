<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHCron

*Planificateur de commandes (remplace MC-Cron)*

<img src="https://img.shields.io/badge/Paper%20%C2%B7%20Bungee%20%C2%B7%20Velocity-26.3-f2a900?style=for-the-badge" alt="Paper · Bungee · Velocity 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale%20%C2%B7%20Lobby%20%C2%B7%20Login%20%C2%B7%20Proxy-3aa655?style=for-the-badge" alt="SkyHand Opale · Lobby · Login · Proxy">

</div>

---

## Présentation

Planificateur de commandes (remplace MC-Cron)

Utilisé sur : **Opale · Lobby · Login · Proxy**.

## Dépendances

- Optionnelles : `PlaceholderAPI`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shcron list` | — | Liste les tâches (état, expression cron, nombre de commandes). | `shcron.admin.list` |
| `/shcron run <tâche>` | — | Déclenche une tâche tout de suite. | `shcron.admin.run` |
| `/shcron reload` | — | Relit `config.yml` et `messages.yml`. Un fichier illisible ne change rien : les tâches en place continuent. | `shcron.admin.reload` |

Sans argument, ou avec une sous-commande inconnue, une ligne d'usage ne liste que les sous-commandes permises à l'expéditeur. Les trois sous-commandes marchent depuis la console, sur Paper comme sur Velocity.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcron.admin` | Toute l'administration (parent des trois nœuds suivants). | `op` |
| `shcron.admin.list` | Lister les tâches. | `op` |
| `shcron.admin.reload` | Recharger la configuration. | `op` |
| `shcron.admin.run` | Déclencher une tâche. | `op` |

Sur Velocity, les nœuds ne se déclarent pas et le parent ne donne pas ses enfants : accorder `shcron.admin` ou le nœud précis dans LuckPerms. Le plugin teste toujours le nœud précis ou `shcron.admin`.

## Messages

Les textes sont dans `messages.yml`, créé à partir du jar s'il manque (dans `plugins/SHCron` sur Paper, `plugins/shcron` sur Velocity).

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHCron/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
