# Application Mobile - SupMap

Application mobile Android de navigation collaborative permettant de signaler et éviter des
incidents sur la route.

## 📦 Technologies utilisées

- [Kotlin](https://kotlinlang.org/)
- [Android SDK](https://developer.android.com/studio)
- [Google Maps API](https://developers.google.com/maps)
- [Retrofit](https://square.github.io/retrofit/) pour les appels API
- [Coroutines Kotlin](https://kotlinlang.org/docs/coroutines-overview.html) pour l'asynchrone
- [ViewModel & Flow](https://developer.android.com/kotlin/flow) pour la gestion d'état
- Architecture MVVM (Model-View-ViewModel)

## 🚀 Fonctionnalités principales

- Connexion sécurisée avec système d'authentification
- Navigation GPS en temps réel
- Signalement d'incidents sur la route (accidents, embouteillages, etc.)
- Recalcul automatique d'itinéraire lors de la détection d'incidents signalés par d'autres
  utilisateurs
- Multiple options d'itinéraires (rapide, économique, sans péage)
- Système collaboratif de confirmation/infirmation des incidents
- Modes de transport variés (voiture, vélo, marche)
- Récupération des itinéraires depuis la version web de SupMap

## 📱 Installation

1. Clonez le dépôt sur votre machine
2. Ouvrez le projet dans Android Studio
3. Synchronisez les dépendances Gradle
4. Lancez l'application sur un émulateur ou un appareil physique

## 🔧 Configuration

Assurez-vous d'avoir configuré votre clé Google Maps API dans le fichier `secrets.xml` (
src/main/res/values/) :

```
MAPS_API_KEY=votre_clé_api_google_maps
```