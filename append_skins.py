
import re

file_path = '/data/Yanlai/minecraft_server_fabric_1.21.1_fresh/npc_skins.md'

new_entries = """

### Billy Hargrove (npc_12_billy_hargrove.json)
- ![](https://www.minecraftskins.com/uploads/skins/2023/03/17/billy-hargrove-21430544.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2023/03/17/billy-hargrove-21430544.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2019/11/14/billy-hargrove--stranger-things--better-w--layers-13623990.png?v936) [Link](https://www.minecraftskins.com/uploads/skins/2019/11/14/billy-hargrove--stranger-things--better-w--layers-13623990.png?v936)
- ![](https://www.skindex.pro/static/skins/billy-hargrove-28MmfnAxCmXygNz3GH2kZC.png) [Link](https://www.skindex.pro/static/skins/billy-hargrove-28MmfnAxCmXygNz3GH2kZC.png)
- ![](https://mcskins.top/wp-content/files/2021/02/billy-hargrove-from-stranger-thing-minecraft-skin-1612644781.png) [Link](https://mcskins.top/wp-content/files/2021/02/billy-hargrove-from-stranger-thing-minecraft-skin-1612644781.png)

### Erica Sinclair (npc_14_erica_sinclair.json)
- ![](https://www.minecraftskins.com/uploads/skins/2019/08/03/erica-sinclair--stranger-things--13281680.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2019/08/03/erica-sinclair--stranger-things--13281680.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2020/11/04/erica-sinclair-15667984.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2020/11/04/erica-sinclair-15667984.png?v938)

### Murray Bauman (npc_15_murray_bauman.json)
- ![](https://www.minecraftskins.com/uploads/skins/2020/09/26/murray-bauman--compromised--15358751.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2020/09/26/murray-bauman--compromised--15358751.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2020/09/25/murray-bauman--taking-down-the-man--15353627.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2020/09/25/murray-bauman--taking-down-the-man--15353627.png?v938)

### Martin Brenner (npc_16_martin_brenner.json)
- ![](https://www.minecraftskins.com/uploads/skins/2022/09/10/martin-brenner-in-suit-20834988.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2022/09/10/martin-brenner-in-suit-20834988.png?v938)

### Beverly Marsh (npc_20_beverly_marsh.json)
- ![](https://www.minecraftskins.com/uploads/skins/2022/06/06/beverly-marsh----20403050.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2022/06/06/beverly-marsh----20403050.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2019/10/28/beverly-marsh---it-13599501.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2019/10/28/beverly-marsh---it-13599501.png?v938)

### Richie Tozier (npc_21_richie_tozier.json)
- ![](https://www.minecraftskins.com/uploads/skins/2021/07/17/richie-tozier-18423400.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2021/07/17/richie-tozier-18423400.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2023/01/27/richie-tozier-21276652.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2023/01/27/richie-tozier-21276652.png?v938)

### Eddie Kaspbrak (npc_22_eddie_kaspbrak.json)
- ![](https://www.minecraftskins.com/uploads/skins/2019/01/01/eddie-kaspbrak-12697258.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2019/01/01/eddie-kaspbrak-12697258.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2017/10/25/skin_20171025002825160792.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2017/10/25/skin_20171025002825160792.png?v938)

### Ben Hanscom (npc_23_ben_hanscom.json)
- ![](https://www.minecraftskins.com/uploads/skins/2019/10/28/ben-hanscom---it-13600733.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2019/10/28/ben-hanscom---it-13600733.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2017/09/16/skin_20170916125650110800.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2017/09/16/skin_20170916125650110800.png?v938)

### Mike Hanlon (npc_24_mike_hanlon.json)
- ![](https://www.minecraftskins.com/uploads/skins/2018/03/18/skin_20180318080839176067.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2018/03/18/skin_20180318080839176067.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2017/08/30/skin_20170830030659157777.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2017/08/30/skin_20170830030659157777.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2019/10/28/mike-hanlon----it-13598970.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2019/10/28/mike-hanlon----it-13598970.png?v938)

### Stanley Uris (npc_25_stanley_uris.json)
- ![](https://www.minecraftskins.com/uploads/skins/2018/11/21/stanley-uris-12604885.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2018/11/21/stanley-uris-12604885.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2019/10/28/stanley-uris---it-13599150.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2019/10/28/stanley-uris---it-13599150.png?v938)

### Patrick Hockstetter (npc_28_patrick_hockstetter.json)
- ![](https://www.minecraftskins.com/uploads/skins/2024/12/07/patrick-hockstetter-22920601.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2024/12/07/patrick-hockstetter-22920601.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2021/01/21/patrick-hockstetter-16453387.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2021/01/21/patrick-hockstetter-16453387.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2023/08/21/patrick-hockstetter--credits-to--skillgottengains--21913241.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2023/08/21/patrick-hockstetter--credits-to--skillgottengains--21913241.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2023/08/22/patrick-hockstetter-v2--credits-to--skillgottengains--21916715.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2023/08/22/patrick-hockstetter-v2--credits-to--skillgottengains--21916715.png?v938)

### The Leper (npc_34_the_leper.json)
- ![](https://www.minecraftskins.com/uploads/skins/2015/03/06/skin_20150306090524119899.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2015/03/06/skin_20150306090524119899.png?v938)

### Lucy MacLean (npc_37_lucy_maclean.json)
- ![](https://static.planetminecraft.com/files/image/minecraft/skin/2024/937/skinseedskin-1717541921683-17887845_iso_l.png) [Link](https://static.planetminecraft.com/files/image/minecraft/skin/2024/937/skinseedskin-1717541921683-17887845_iso_l.png)

### Dogmeat (npc_40_dogmeat.json)
- ![](https://static.planetminecraft.com/files/image/minecraft/mob-skin/2020/269/wolf-planetminecraft-com-13703940_iso_l.png) [Link](https://static.planetminecraft.com/files/image/minecraft/mob-skin/2020/269/wolf-planetminecraft-com-13703940_iso_l.png)

### Codsworth (npc_41_codsworth.json)
- ![](https://www.minecraftskins.com/uploads/skins/2020/11/04/codsworth--fallout-4--1606883207.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2020/11/04/codsworth--fallout-4--1606883207.png?v938)
- ![](https://static.planetminecraft.com/files/resource_media/preview/1834/fallout-4-skin-4-codsworth-12349132_minecraft_skin-12349132.jpg) [Link](https://static.planetminecraft.com/files/resource_media/preview/1834/fallout-4-skin-4-codsworth-12349132_minecraft_skin-12349132.jpg)

### Preston Garvey (npc_42_preston_garvey.json)
- ![](https://www.minecraftskins.com/uploads/skins/2015/12/29/skin_20151229215555122274.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2015/12/29/skin_20151229215555122274.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2025/07/09/preston-garvey-fallout-4-23389002.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2025/07/09/preston-garvey-fallout-4-23389002.png?v938)
- ![](https://static.planetminecraft.com/files/resource_media/preview/1544/armband9534182_minecraft_skin-9534182.jpg) [Link](https://static.planetminecraft.com/files/resource_media/preview/1544/armband9534182_minecraft_skin-9534182.jpg)

### Piper Wright (npc_43_piper_wright.json)
- ![](https://static.planetminecraft.com/files/image/minecraft/skin/2025/571/piper-19137205_iso_l.png) [Link](https://static.planetminecraft.com/files/image/minecraft/skin/2025/571/piper-19137205_iso_l.png)
- ![](https://static.planetminecraft.com/files/resource_media/preview/1703/download110810205_minecraft_skin-10810205.jpg) [Link](https://static.planetminecraft.com/files/resource_media/preview/1703/download110810205_minecraft_skin-10810205.jpg)

### Nick Valentine (npc_44_nick_valentine.json)
- ![](https://www.minecraftskins.com/uploads/skins/2024/04/25/nick-valentine--read-description-lol--22495969.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2024/04/25/nick-valentine--read-description-lol--22495969.png?v938)
- ![](https://static.planetminecraft.com/files/resource_media/preview/1624/nickvalentineshaded10260870_minecraft_skin-10260870.jpg) [Link](https://static.planetminecraft.com/files/resource_media/preview/1624/nickvalentineshaded10260870_minecraft_skin-10260870.jpg)

### Elder Maxson (npc_46_elder_maxson.json)
- ![](https://www.minecraftskins.com/uploads/skins/2022/01/18/elder-maxson-19738057.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2022/01/18/elder-maxson-19738057.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2023/07/09/elder-maxson-21774228.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2023/07/09/elder-maxson-21774228.png?v938)
- ![](https://www.minecraftskins.com/uploads/skins/2021/01/26/elder-arthur-maxson-16506463.png?v938) [Link](https://www.minecraftskins.com/uploads/skins/2021/01/26/elder-arthur-maxson-16506463.png?v938)

"""

with open(file_path, 'a') as f:
    f.write(new_entries)
