package com.boostplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class BoostPlugin extends JavaPlugin implements Listener {

    private NamespacedKey KIND;
    private NamespacedKey OWNER;
    private NamespacedKey OWNER_NAME;

    private final Random rnd = new Random();
    private final Map<UUID, Long> veinCooldown = new HashMap<>();
    private final Map<UUID, Long> phoenixCooldown = new HashMap<>();
    private static final long VEIN_MS = 10_000L;
    private static final long PHOENIX_MS = 600_000L;

    private static final Set<String> LOOT_TABLES = Set.of(
            "minecraft:chests/woodland_mansion",
            "minecraft:chests/underwater_ruin_small",
            "minecraft:chests/underwater_ruin_big",
            "minecraft:chests/ancient_city",
            "minecraft:chests/ancient_city_ice_box"
    );

    private final List<Material> orePool = new ArrayList<>();

    @Override
    public void onEnable() {
        KIND = new NamespacedKey(this, "kind");
        OWNER = new NamespacedKey(this, "owner");
        OWNER_NAME = new NamespacedKey(this, "owner_name");

        addOre(Material.COAL, 4);
        addOre(Material.RAW_COPPER, 3);
        addOre(Material.RAW_IRON, 3);
        addOre(Material.LAPIS_LAZULI, 2);
        addOre(Material.REDSTONE, 2);
        addOre(Material.RAW_GOLD, 2);
        addOre(Material.DIAMOND, 1);
        addOre(Material.EMERALD, 1);

        registerRecipes();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickEffects, 20L, 20L);
        getLogger().info("BoostPlugin active : 7 boosts charges.");
    }

    private void addOre(Material m, int weight) {
        for (int i = 0; i < weight; i++) orePool.add(m);
    }

    // ------------------------------------------------------------------
    // Objets
    // ------------------------------------------------------------------

    private Component text(String s, TextColor c) {
        return Component.text(s).color(c).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack make(String kind, Boost b, UUID owner, String ownerName) {
        Material mat = switch (kind) {
            case "fragment" -> Material.AMETHYST_SHARD;
            case "core" -> Material.ECHO_SHARD;
            case "template" -> Material.GLOBE_BANNER_PATTERN;
            case "charm" -> Material.PRISMARINE_CRYSTALS;
            default -> Material.PLAYER_HEAD;
        };
        String label = switch (kind) {
            case "fragment" -> "Fragment";
            case "core" -> "Pierre de boost";
            case "template" -> "Template de boost";
            case "charm" -> "Amulette";
            default -> "Poupée d'âme";
        };
        boolean bindable = kind.equals("charm") || kind.equals("doll");

        ItemStack st = new ItemStack(mat);
        ItemMeta m = st.getItemMeta();
        TextColor col = (b != null) ? b.color : NamedTextColor.WHITE;
        String title = (b != null) ? label + " : " + b.displayName : label;
        m.displayName(text(title, col));

        List<Component> lore = new ArrayList<>();
        if (b != null) lore.add(text(b.desc, NamedTextColor.GRAY));
        if (kind.equals("charm")) {
            lore.add(text("Garde-la dans ton inventaire", NamedTextColor.DARK_GRAY));
            m.setEnchantmentGlintOverride(true);
        }
        if (bindable) {
            lore.add(owner == null
                    ? text("Non liée", NamedTextColor.DARK_GRAY)
                    : text("Liée à " + ownerName, NamedTextColor.YELLOW));
        }
        m.lore(lore);

        m.getPersistentDataContainer().set(KIND, PersistentDataType.STRING,
                kind + ":" + (b == null ? "" : b.id));
        if (bindable && owner != null) {
            m.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner.toString());
            m.getPersistentDataContainer().set(OWNER_NAME, PersistentDataType.STRING, ownerName);
            if (m instanceof SkullMeta sm) {
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            }
        }
        st.setItemMeta(m);
        return st;
    }

    private String tag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KIND, PersistentDataType.STRING);
    }

    private String kindOf(ItemStack it) {
        String t = tag(it);
        return t == null ? null : t.split(":", 2)[0];
    }

    private Boost boostOf(ItemStack it) {
        String t = tag(it);
        if (t == null || !t.contains(":")) return null;
        return Boost.byId(t.substring(t.indexOf(':') + 1));
    }

    private UUID ownerOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String ownerNameOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return "?";
        String s = it.getItemMeta().getPersistentDataContainer().get(OWNER_NAME, PersistentDataType.STRING);
        return s == null ? "?" : s;
    }

    // ------------------------------------------------------------------
    // Recettes
    // ------------------------------------------------------------------

    private NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    private void registerRecipes() {
        // Poupee d'ame (le proprietaire est fixe au moment du craft)
        ShapedRecipe doll = new ShapedRecipe(key("soul_doll"), make("doll", null, null, null));
        doll.shape("IWI", "WTW", "ICI");
        doll.setIngredient('I', Material.IRON_INGOT);
        doll.setIngredient('W', Material.WHITE_WOOL);
        doll.setIngredient('T', Material.TOTEM_OF_UNDYING);
        doll.setIngredient('C', Material.HEART_OF_THE_SEA);
        Bukkit.addRecipe(doll);

        for (Boost b : Boost.values()) {
            // 9 fragments -> pierre de boost
            ShapelessRecipe core = new ShapelessRecipe(key("core_" + b.id), make("core", b, null, null));
            RecipeChoice frag = new RecipeChoice.ExactChoice(make("fragment", b, null, null));
            for (int i = 0; i < 9; i++) core.addIngredient(frag);
            Bukkit.addRecipe(core);

            // Recette en U : 7 diamants + bloc de netherite + pierre de boost -> template
            ShapedRecipe tpl = new ShapedRecipe(key("template_" + b.id), make("template", b, null, null));
            tpl.shape("DCD", "DND", "DDD");
            tpl.setIngredient('D', Material.DIAMOND);
            tpl.setIngredient('N', Material.NETHERITE_BLOCK);
            tpl.setIngredient('C', new RecipeChoice.ExactChoice(make("core", b, null, null)));
            Bukkit.addRecipe(tpl);

            // Table de forgeron : template + poupee + lingot de netherite -> amulette
            SmithingTransformRecipe smith = new SmithingTransformRecipe(
                    key("charm_" + b.id),
                    make("charm", b, null, null),
                    new RecipeChoice.ExactChoice(make("template", b, null, null)),
                    new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD),
                    new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT));
            Bukkit.addRecipe(smith);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() instanceof Keyed k && k.getKey().equals(key("soul_doll"))) {
            if (e.getView().getPlayer() instanceof Player p) {
                e.getInventory().setResult(make("doll", null, p.getUniqueId(), p.getName()));
            }
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent e) {
        ItemStack res = e.getResult();
        if (res == null || !"charm".equals(kindOf(res))) return;
        SmithingInventory inv = e.getInventory();
        ItemStack base = inv.getInputEquipment();
        UUID owner = ownerOf(base);
        if (base == null || !"doll".equals(kindOf(base)) || owner == null) {
            e.setResult(null); // il faut une vraie poupee liee
            return;
        }
        Boost b = boostOf(res);
        if (b == null) return;
        e.setResult(make("charm", b, owner, ownerNameOf(base)));
    }

    // ------------------------------------------------------------------
    // Effets
    // ------------------------------------------------------------------

    private Set<Boost> active(Player p) {
        EnumSet<Boost> set = EnumSet.noneOf(Boost.class);
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !"charm".equals(kindOf(it))) continue;
            UUID o = ownerOf(it);
            if (o == null || !o.equals(p.getUniqueId())) continue; // amulette liee a un autre joueur
            Boost b = boostOf(it);
            if (b != null) set.add(b);
        }
        return set;
    }

    private void eff(Player p, PotionEffectType t, int amp, int dur) {
        p.addPotionEffect(new PotionEffect(t, dur, amp, true, false, true));
    }

    private void tickEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Boost b : active(p)) {
                switch (b) {
                    case ZEPHYR -> {
                        eff(p, PotionEffectType.SPEED, 1, 100);
                        eff(p, PotionEffectType.JUMP_BOOST, 1, 100);
                    }
                    case TITAN -> eff(p, PotionEffectType.STRENGTH, 1, 100);
                    case SKIN -> {
                        eff(p, PotionEffectType.RESISTANCE, 1, 100);
                        eff(p, PotionEffectType.FIRE_RESISTANCE, 0, 100);
                    }
                    case ABYSS -> {
                        eff(p, PotionEffectType.WATER_BREATHING, 0, 100);
                        eff(p, PotionEffectType.DOLPHINS_GRACE, 0, 100);
                        if (p.isUnderWater()) eff(p, PotionEffectType.NIGHT_VISION, 0, 260);
                    }
                    case GREEN -> {
                        eff(p, PotionEffectType.HASTE, 1, 100);
                        eff(p, PotionEffectType.SATURATION, 0, 40);
                    }
                    case PHOENIX -> eff(p, PotionEffectType.REGENERATION, 0, 100);
                    default -> { }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Set<Boost> a = active(p);
        if (a.isEmpty()) return;

        // Zephyr : pas de degats de chute
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && a.contains(Boost.ZEPHYR)) {
            e.setCancelled(true);
            return;
        }

        // Phenix : resurrection, 1 fois toutes les 10 minutes
        if (a.contains(Boost.PHOENIX) && p.getHealth() - e.getFinalDamage() <= 0) {
            long now = System.currentTimeMillis();
            Long last = phoenixCooldown.get(p.getUniqueId());
            if (last == null || now - last >= PHOENIX_MS) {
                phoenixCooldown.put(p.getUniqueId(), now);
                e.setCancelled(true);
                p.setHealth(10.0);
                p.setFireTicks(0);
                eff(p, PotionEffectType.REGENERATION, 1, 200);
                eff(p, PotionEffectType.ABSORPTION, 1, 200);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.3);
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                p.sendMessage(text("Le Phénix te ramène à la vie !", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!active(p).contains(Boost.VEIN)) return;
        long now = System.currentTimeMillis();
        Long last = veinCooldown.get(p.getUniqueId());
        if (last != null && now - last < VEIN_MS) return;
        veinCooldown.put(p.getUniqueId(), now);

        Location base = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5));
        for (int i = 0; i < 7; i++) {
            Material ore = orePool.get(rnd.nextInt(orePool.size()));
            Location l = base.clone().add(rnd.nextDouble() - 0.5, -0.5, rnd.nextDouble() - 0.5);
            p.getWorld().dropItem(l, new ItemStack(ore, 1 + rnd.nextInt(2)));
        }
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, base, 20, 0.5, 0.5, 0.5, 0.0);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    // ------------------------------------------------------------------
    // Loot : fragments dans les manoirs, ruines sous-marines, anciennes cites
    // ------------------------------------------------------------------

    @EventHandler
    public void onLoot(LootGenerateEvent e) {
        String k = e.getLootTable().getKey().toString();
        if (!LOOT_TABLES.contains(k)) return;
        if (rnd.nextDouble() > 0.45) return;
        Boost b = Boost.values()[rnd.nextInt(Boost.values().length)];
        ItemStack f = make("fragment", b, null, null);
        f.setAmount(1 + rnd.nextInt(2));
        e.getLoot().add(f);
    }

    // ------------------------------------------------------------------
    // Commandes
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("boosts")) {
            sender.sendMessage(text("— Boosts : recettes —", NamedTextColor.GOLD));
            sender.sendMessage(text("Poupée : fer/laine en coins, totem au centre, cœur de la mer en bas.", NamedTextColor.GRAY));
            sender.sendMessage(text("Fragments : à trouver dans manoirs, ruines sous-marines, anciennes cités.", NamedTextColor.GRAY));
            sender.sendMessage(text("9 fragments identiques = Pierre de boost.", NamedTextColor.GRAY));
            sender.sendMessage(text("Template : 7 diamants en U, bloc de netherite au centre, pierre de boost en haut.", NamedTextColor.GRAY));
            sender.sendMessage(text("Forgeron : template + poupée + lingot de netherite = Amulette (liée à toi).", NamedTextColor.GRAY));
            StringBuilder sb = new StringBuilder("Boosts : ");
            for (Boost b : Boost.values()) sb.append(b.displayName).append(", ");
            sender.sendMessage(text(sb.substring(0, sb.length() - 2), NamedTextColor.YELLOW));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("boostgive")) {
            if (args.length < 2) return false;
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t == null) {
                sender.sendMessage(text("Joueur introuvable.", NamedTextColor.RED));
                return true;
            }
            String kind = args[1].toLowerCase();
            Boost b = args.length > 2 ? Boost.byId(args[2]) : null;
            boolean needsBoost = !kind.equals("doll");
            if (!List.of("fragment", "core", "template", "charm", "doll").contains(kind)
                    || (needsBoost && b == null)) {
                sender.sendMessage(text("Types : fragment, core, template, charm, doll. Boosts : vein, zephyr, titan, skin, abyss, green, phoenix.", NamedTextColor.RED));
                return true;
            }
            t.getInventory().addItem(make(kind, b, t.getUniqueId(), t.getName()));
            sender.sendMessage(text("Objet donné à " + t.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        return false;
    }
}
