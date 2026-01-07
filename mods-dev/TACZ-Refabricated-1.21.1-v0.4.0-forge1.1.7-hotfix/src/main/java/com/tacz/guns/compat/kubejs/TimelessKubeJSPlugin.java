package com.tacz.guns.compat.kubejs;

public class TimelessKubeJSPlugin /*extends KubeJSPlugin*/ {
//    public static final String KUBEJS_MODID = "kubejs";
//    private static final Map<String, AbstractGunItem> GUNTYPE_REGISTER_MAP = new HashMap<>();
//
//    @Override
//    public void init() {
//        RegistryInfo.ITEM.addType("tacz_gun", CustomGunItemBuilder.class, CustomGunItemBuilder::new);
//    }
//
//    @Override
//    public void registerEvents() {
//        //提早加载防止出现问题
//        TimelessCommonEvents.INSTANCE.init();
//        TimelessServerEvents.INSTANCE.init();
//        TimelessClientEvents.INSTANCE.init();
//        GunKubeJSEvents.GROUP.register();
//    }
//
//    @Override
//    public void registerBindings(BindingsEvent event) {
//        event.add("TimelessItem", TimelessItemWrapper.class);
//        event.add("GunProperties", GunProperties.class);
//        event.add("GunSmithTableResultInfo", GunSmithTableResultInfo.class);
//    }
//
//    @Override
//    public void registerTypeWrappers(ScriptType type, TypeWrappers typeWrappers) {
//        typeWrappers.registerSimple(GunSmithTableResultInfo.class, GunSmithTableResultInfo::of);
//    }
//
//    @Override
//    public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
//        event.namespace(GunMod.MOD_ID).register("gun_smith_table_crafting", TimelessGunSmithTableRecipeSchema.SCHEMA);
//    }
//
//    @Override
//    public void registerRecipeComponents(RecipeComponentFactoryRegistryEvent event) {
//        event.register("gunSmithTableResultInfo", GunSmithTableResultComponents.RESULT_INFO);
//    }
//
//    public static void registerGunType(String typeName, AbstractGunItem registryObject) {
//        GUNTYPE_REGISTER_MAP.put(typeName, registryObject);
//    }
//
//    public static void onItemRegister() {
//        if (FabricLoader.getInstance().isModLoaded(KUBEJS_MODID)) {
//            for (Map.Entry<String, AbstractGunItem> entry : GUNTYPE_REGISTER_MAP.entrySet()) {
//                GunItemManager.registerGunItem(entry.getKey(), entry.getValue());
//            }
//        }
//    }
}
