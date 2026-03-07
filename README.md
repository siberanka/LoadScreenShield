# LoadScreenShield

**LoadScreenShield** is a highly optimized, fully customizable Minecraft server plugin designed specifically to protect players while they are downloading and applying resource packs. **It provides an ItemsAdder-like protection for servers where the resource pack is sent via the proxy.** Written with native support for **Folia** and **Paper** 1.21.x architectures.

## Features ✨
* **Complete Invulnerability**: Prevents players from doing or receiving damage, moving, dropping items, placing/breaking blocks, interacting with inventories, and using commands while the resource pack prompt is active on their screen.
* **100% Folia Supported**: Eliminates main-thread blocking by securely operating on Bukkit's RegionScheduler, minimizing TPS loss and crashes.
* **PacketEvents Injection**: Uses PacketEvents 2.x API directly to send purely virtual 5x5x5 black wool/concrete boxes directly to the client to simulate a fake loading screen ("blindness box") without any real block updates.
* **Repeating Actionbar & Title Display**: Continuously re-sends visually pleasing, prefix-less `Title` warnings every single second while the target is shielded, ensuring that even under severe client-side loading delays, the user will see the prompt securely. 
* **Seamless Bedrock Ignore**: Auto-detects Floodgate UUIDs and ignores Geyser/Bedrock clients entirely since their resource pack delivery methods differ.
* **Multi-Language Support**: Complete message customization supporting multiple configurable languages. Included languages are `en` and `tr`. Configuration scales safely with dynamic config reloads.
* **Dynamic Loading Support**: Fully compatible with tools like PlugMan. Handles its connections properly without causing `NullPointerException`s when reloading.

---

## Commands and Permissions 📜
* `/loadscreenshield reload` (`loadscreenshield.admin`): Reloads the configuration and language configurations dynamically without requiring a server reboot.

---

## Installation ⚙️
1. Require **PacketEvents 2.x**. Ensure that a version of PacketEvents 2.11 or newer is installed on your proxy/server as a plugin because LoadScreenShield hooks into it.
2. Ensure you are running Java 21+ and an updated fork of Paper or Folia.
3. Drop `LoadScreenShield-1.0.0.jar` into your `/plugins/` folder and boot your server.

---
---

# LoadScreenShield (Türkçe)

**LoadScreenShield**, oyuncular sunucu kaynak paketini indirip uygularken (Resource Pack yüklenme ekranında beklerken) onları tam korumaya almak için geliştirilmiş, yüksek performanslı bir Minecraft eklentisidir. **Kaynak paketinin proxy üzerinden gönderildiği sunucularda oyuncuya ItemsAdder'ın korumasına benzer bir koruma sağlar.** **Folia** ve **Paper** (1.21.x) altyapılarına %100 uyumlu olarak yazılmıştır.

## Özellikler ✨
* **Tam Dokunulmazlık ve Koruması**: Kaynak paketi yükleme ekranındayken oyuncuların hasar almasını, hareket etmesini, eşya atmasını, blok koyup/kırmasını ve komut kullanmasını engeller. Tam bir geçici güvenli bölge sağlar.
* **%100 Folia Uyumluluğu**: Sunucunun ana iş parçacığını (Main Thread) asla yormaz. `RegionScheduler` kullanarak Folia'nın tps ve eşzamanlama metotlarına harfiyen uyar. Çökme (Crash) yaşanmaz.
* **PacketEvents Teknolojisi**: Sunucuyu kastıracak gerçek blok koyma işlemleri yerine, PacketEvents API'si kullanarak oyuncunun bulunduğu alana istemci (client) tarafında gözüken sahte siyah yün kutuları gönderir. Ciddi bir optimizasyon sağlar.
* **Sürekli Yinelenen Ekran Başlığı (Title)**: Oyuncu koruma altındayken saniyede 1 kez kendini otomatik olarak tekrarlayan başlık (Title) ve ActionBar mesajları gönderir. Böylece oyuncunun ekranı geç yüklense bile yazıları kaçırmaması ve güvenle beklemesi sağlanır. 
* **Kusursuz Bedrock/Geyser Uyumu**: Floodgate eklentisini tanıyıp Bedrock (Telefondan) giren oyunculara işlem uygulamaz. Çünkü Bedrock oyuncularının paket indirme sistemi Java'dan tamamen farklıdır.
* **Çoklu Dil Sistemi**: Sisteme ait tüm başlık ve mesajlar %100 ayarlanabilir formattadır. Standart olarak İngilizce (`en.yml`) ve Türkçe (`tr.yml`) dilleri entegre olarak gelir.
* **Dinamik Yükleme (PlugMan) Desteği**: Sunucu aktifken eklentiyi yükleyip silebilirsiniz. PaketEvents dinleyicileri çökmelere (`NullPointerException`) karşı tam bir koruma ile sarmalanmıştır.

---

## Komutlar ve Yetkiler 📜
* `/loadscreenshield reload` (`loadscreenshield.admin`): Sunucuyu yeniden başlatmaya gereksinim duymadan `config.yml` ve dil dosyalarındaki (`lang/`) değişiklikleri anında aktif eder.

---

## Kurulum ⚙️
1. Sunucunuzda **PacketEvents 2.x** eklentisinin harici olarak yüklü olduğundan (v2.11+) emin olun. Eklenti optimizasyon gereği bu API'yi kullanır.
2. Java 21+ destekleyen güncel bir Paper veya Folia sunucu motoru kullandığınızdan emin olun.
3. `LoadScreenShield-1.0.0.jar` dosyasını `/plugins/` klasörüne atıp sunucunuzu başlatın.
