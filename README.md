# Helio

A modern, highly responsive Android Calling Application built with Jetpack Compose.

## Approach

- **UI/UX Design**: Built exclusively with Jetpack Compose following Material Design principles, utilizing dark mode and glassmorphism elements. It uses dynamic fluid animations (`FastOutSlowInEasing`) and gradient visuals for a premium look.
- **Robust Architecture**: Follows the MVVM (Model-View-ViewModel) architectural pattern. The core state is cleanly decoupled via `CallState` variants (`Idle`, `Calling`, `Ringing`, `Active`, `Ended`). This manages complex asynchronous calling flows transparently.
- **Telecom Integration**: Manages native incoming and outgoing calls securely utilizing `TelecomManager` and the `InCallService` API.
- **Permissions Aware**: The app natively simulates calling logic for an uninterrupted user experience even if Android phone permissions (`CALL_PHONE`, `READ_CALL_LOG`) are missing or denied.
- **Performant**: Built securely with coroutine Flow pipelines. High-cost UI components (like gradient shader allocations) are hoisted and statically rendered to maintain 60FPS list scrolling. Contact indexing operates gracefully on background dispatchers to keep UI typing buttery smooth.
