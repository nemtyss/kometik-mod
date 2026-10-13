# Kozmetik Menüsü (Fabric 1.21.1)

Oyun içinden tek bir menüyle **kılıç**, **totem** ve **oyuncu skini** seçebildiğin, **vuruş / öldürme efektleri** ekleyebildiğin ve **menü rengini ayarlardan değiştirebildiğin** istemci (client) modu.

> Mod sadece **senin ekranında** çalışır. Sunucuya mod kurmaya gerek yok, her sunucuda kullanılır. Başkaları senin kılıç/totem/skin görünümünü **görmez**.

## Kurulum (oynamak için)

1. Minecraft **1.21.1** için [Fabric Loader](https://fabricmc.net/use/installer/) kur.
2. [Fabric API](https://modrinth.com/mod/fabric-api) (1.21.1 sürümü) indir ve `mods` klasörüne at.
3. Derlenmiş `kozmetik-1.0.0.jar` dosyasını `.minecraft/mods` klasörüne at. (Jar'ı nasıl elde edeceğin aşağıda.)
4. Oyunda **K** tuşuna bas (ya da sohbete `/kozmetik` yaz). Tuşu Kontroller menüsünden değiştirebilirsin.

## Menü

| Sekme | Ne yapar |
|---|---|
| **Kılıçlar** | 24 hazır kılıç dokusu + kendi PNG'lerin. Seçtiğin doku elde, envanterde, yerde, her yerde tüm kılıç türlerine (tahta … netherit) uygulanır. |
| **Totemler** | 12 hazır totem + kendi PNG'lerin. Ölümsüzlük totemi patlama animasyonunda da görünür. |
| **Skinler** | **Oyuncu skinini değiştirir.** Kendi skin PNG'ni klasöre atarsın ya da **oyuncu adı yazıp "Skini Al"** dersin (skin Mojang'dan indirilir ve klasöre kaydedilir). |
| **Efektler** | Vuruş ve öldürme için 20 partikül efekti, yoğunluk ayarı ve "Önizle" düğmesi. |
| **Ayarlar** | Menü rengi (hazır renkler + R/G/B kaydırıcıları), panel rengi ve opaklığı, arka plan karartma/bulanıklık, köşe yuvarlaklığı, parlama, gökkuşağı teması ve her özelliği aç/kapa. |

Kısayollar: `1`–`5` sekme değiştirir, `←`/`→` ya da fare tekerleği sayfa değiştirir, `ESC` veya `K` kapatır.

## Kendi dosyalarını ekleme

Oyunu bir kez açtıktan sonra `.minecraft/kozmetik/` klasörü oluşur:

```
kozmetik/
  kilic/   -> kılıç dokuları (.png)
  totem/   -> totem dokuları (.png)
  skin/    -> oyuncu skinleri (.png, 64x64 veya 64x32)
```

* Dosyaları koy, menüde **Yenile** düğmesine bas. Dosya adı menüde isim olarak görünür.
* Menüdeki **Klasörü Aç** düğmesi klasörü açar.
* Kılıç/totem dokusu şeffaf arka planlı olmalı. Normal boyut 16x16'dır. Daha büyük (ör. 32x32, 64x64) dosyalar otomatik 16x16'ya küçültülür; en net sonuç 16x16'dır. Kılıç, normal Minecraft kılıcı gibi **sol alttan sağ üste** çizilmiş olmalı.
* İnce (Alex) kollu skin için dosya adının içine `slim` yaz (ör. `ejder_slim.png`). Oyuncu adından indirilen skinlerde bu otomatik ayarlanır.

## Jar dosyasını derleme

### Yol 1: GitHub ile (bilgisayara bir şey kurmadan)

1. GitHub'da yeni bir depo (repository) aç; gizli (private) olabilir.
2. Bu projenin zip'ini çıkar. **Klasörün içindeki her şeyi** depoya yükle (*Add file → Upload files*). Gizli `.github` klasörü de gitmeli (Mac'te Finder'da `Cmd + Shift + .` ile gizli dosyalar görünür).
3. `.github` klasörü yüklenmediyse: *Add file → Create new file* de, dosya adına `.github/workflows/build.yml` yaz ve şunu yapıştır:

```yaml
name: build
on: [push, pull_request, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - run: chmod +x ./gradlew
      - run: ./gradlew build --no-daemon
      - uses: actions/upload-artifact@v4
        with: { name: kozmetik-mod, path: "build/libs/*.jar", if-no-files-found: error }
```

4. Deponun **Actions** sekmesine gir. "build" çalışması birkaç dakikada biter (yeşil tik).
5. Çalışmanın sayfasında en altta **Artifacts → kozmetik-mod**'u indir. Zip'in içindeki `.jar` senin modun.

### Yol 2: Kendi bilgisayarında

Java 21 kurulu olmalı. Proje klasöründe:

```
./gradlew build          (Windows: gradlew.bat build)
```

Jar: `build/libs/kozmetik-1.0.0.jar`. IntelliJ IDEA ile açıp `runClient` görevini çalıştırarak oyunu test de edebilirsin.

## Nasıl çalışıyor (kısaca)

* **Kılıç/totem:** Oyunun eşya atlasındaki ilgili sprite'ın pikselleri doğrudan değiştirilir. Bu yüzden elde, envanterde, yerde, item frame'de aynı doku görünür. Kaynak paketi yeniden yüklenince (F3+T) otomatik yeniden uygulanır.
* **Skin:** Yerel oyuncunun `getSkinTextures()` sonucuna bir mixin ile senin seçtiğin doku verilir.
* **Efektler:** Vuruşta partikül oluşturulur. Hedefin canı 0'a düşünce öldürme efekti oynatılır. Hepsi sadece senin ekranında görünür.
* Ayarlar `config/kozmetik.json` dosyasında saklanır.

## Sorun giderme

* **Menü açılmıyor:** `mods` klasöründe Fabric API olduğundan ve sürümün 1.21.1 olduğundan emin ol. K tuşu başka bir mod tarafından kullanılıyor olabilir, `/kozmetik` komutunu dene.
* **Kılıç değişmiyor:** Ayarlar'da "Kılıç dokusunu uygula" açık mı bak. Kaynak paketin kılıç dokusunu özel olarak (CIT gibi) değiştiriyorsa mod bunu ezmeyebilir.
* **Skin Al çalışmıyor:** İnternet bağlantısını ve oyuncu adının doğruluğunu kontrol et. Mojang sunucularına erişilemezse durum satırında uyarı çıkar.
* **Derleme hatası:** Hata mesajını bana gönder, düzeltirim.
