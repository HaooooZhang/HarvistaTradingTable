package ink.myumoon.tradingtable.trade;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.config.CurrencyBackend;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConversionService {
	private static final String CANDIDATE_NAMESPACES = "random_economy";
	private static final String[] COIN_IDS = {
			"copper_coin",
			"iron_coin",
			"gold_coin",
			"diamond_coin",
			"emerald_coin",
			"netherite_coin"
	};
	private static final long[] COIN_VALUES = {
			1L,
			10L,
			100L,
			1000L,
			10000L,
			100000L
	};

	private ConversionService() {
	}

	public static boolean isEnabled() {
		// 只要识别到已配置货币属于这套币制，就启用换算；
		// 不要求 6 个面额必须全部存在，否则会错误回退到“每个物品=1”计数。
		return !getDenominations().isEmpty();
	}

	public static boolean isRegistered(Item item) {
		// 允许任何在总面额表中的币种都算"已注册"，即使值为 0
		return getDenominations().containsKey(item);
	}

	public static long getValue(Item item) {
		if (item == null) {
			return 0L;
		}
		for (Map.Entry<Item, Long> entry : getDenominations().entrySet()) {
			if (entry.getKey() == item) {
				// 返回相对值，若未登记或相对值为0也直接返回
				return entry.getValue();
			}
		}
		return 0L;
	}

	public static long totalValue(Iterable<ItemStack> stacks) {
		long total = 0L;
		for (ItemStack stack : stacks) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			long value = getValue(stack.getItem());
			if (value > 0L) {
				total += value * stack.getCount();
			}
		}
		return total;
	}

	public static List<ItemStack> convertBalanceToStacks(long balance) {
		if (balance <= 0L) {
			return Collections.emptyList();
		}

		Map<Item, Long> denominations = getDenominations();
		if (denominations.isEmpty()) {
			return Collections.emptyList();
		}

		List<ItemStack> result = new ArrayList<>();
		long remaining = balance;
		for (Map.Entry<Item, Long> entry : denominations.entrySet()) {
			long value = entry.getValue();
			if (value <= 0L) {
				continue;
			}
			long count = remaining / value;
			if (count <= 0L) {
				continue;
			}
			int give = (int) Math.min(Integer.MAX_VALUE, count);
			result.add(new ItemStack(entry.getKey(), give));
			remaining -= value * give;
			if (remaining <= 0L) {
				break;
			}
		}
		return result;
	}

	public static Optional<List<ItemStack>> tryFillPayment(long requiredAmount, Map<Item, Integer> available) {
		if (requiredAmount <= 0L) {
			return Optional.of(Collections.emptyList());
		}
		Map<Item, Long> denominations = getDenominations();
		if (denominations.isEmpty()) {
			return Optional.empty();
		}

		List<Map.Entry<Item, Long>> entries = new ArrayList<>(denominations.entrySet());
		PaymentPlan plan = selectBestPayment(entries, 0, requiredAmount, available);
		return plan == null ? Optional.empty() : Optional.of(plan.stacks);
	}

	private static PaymentPlan selectBestPayment(List<Map.Entry<Item, Long>> entries, int index, long requiredAmount,
									Map<Item, Integer> available) {
		if (requiredAmount <= 0L) {
			return new PaymentPlan(0L, new ArrayList<>());
		}
		if (index >= entries.size()) {
			return null;
		}

		Map.Entry<Item, Long> entry = entries.get(index);
		Item item = entry.getKey();
		long value = entry.getValue();
		int count = Math.max(0, available.getOrDefault(item, 0));

		PaymentPlan best = null;
		long exactUse = Math.min(count, requiredAmount / value);

		// 方案1：按当前面额的“精确最大值”取
		PaymentPlan exactTail = selectBestPayment(entries, index + 1, requiredAmount - exactUse * value, available);
		if (exactTail != null) {
			best = prepend(entry, exactUse, value, exactTail);
		}

		// 方案2：如果当前面额还能多取 1 个，则允许超额支付并停止继续向下拆
		if (exactUse < count) {
			long overpay = (exactUse + 1L) * value;
			PaymentPlan overpayPlan = new PaymentPlan(overpay, new ArrayList<>());
			overpayPlan.stacks.add(new ItemStack(item, (int) (exactUse + 1L)));
			best = chooseBetter(best, overpayPlan);
		}

		return best;
	}

	private static PaymentPlan prepend(Map.Entry<Item, Long> entry, long use, long value, PaymentPlan tail) {
		List<ItemStack> stacks = new ArrayList<>();
		if (use > 0L) {
			stacks.add(new ItemStack(entry.getKey(), (int) use));
		}
		stacks.addAll(tail.stacks);
		return new PaymentPlan(use * value + tail.paid, stacks);
	}

	private static PaymentPlan chooseBetter(PaymentPlan a, PaymentPlan b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		if (b.paid != a.paid) {
			return b.paid < a.paid ? b : a;
		}
		return b.stacks.size() < a.stacks.size() ? b : a;
	}

	private record PaymentPlan(long paid, List<ItemStack> stacks) {
	}

	private static Map<Item, Long> getDenominations() {
		if (Config.getCurrencyBackend() != CurrencyBackend.ITEM || !Boolean.TRUE.equals(Config.getCompatibilityMode())) {
			return Collections.emptyMap();
		}

		Item configured = Config.getCurrencyItem();
		boolean configuredIsCoin = false;
		long configuredCoinValue = 0L;
		Map<Item, Long> denominations = new LinkedHashMap<>();

		// 第一遍：找到配置货币的原始面额值
		for (int i = 0; i < COIN_IDS.length; i++) {
			ResourceLocation id = ResourceLocation.tryParse(CANDIDATE_NAMESPACES + ":" + COIN_IDS[i]);
			if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
				continue;
			}

			Item item = BuiltInRegistries.ITEM.get(id);
			if (item == configured) {
				configuredIsCoin = true;
				configuredCoinValue = COIN_VALUES[i];
				break;
			}
		}

		if (!configuredIsCoin || configuredCoinValue <= 0L) {
			return Collections.emptyMap();
		}

		// 第二遍：相对于配置货币归一化所有面额
		for (int i = 0; i < COIN_IDS.length; i++) {
			ResourceLocation id = ResourceLocation.tryParse(CANDIDATE_NAMESPACES + ":" + COIN_IDS[i]);
			if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
				continue;
			}

			Item item = BuiltInRegistries.ITEM.get(id);
			long normalized = COIN_VALUES[i] / configuredCoinValue;
			if (normalized > 0L) {
				denominations.put(item, normalized);
			}
		}

		List<Map.Entry<Item, Long>> entries = new ArrayList<>(denominations.entrySet());
		entries.sort(Map.Entry.<Item, Long>comparingByValue(Comparator.reverseOrder()));

		Map<Item, Long> ordered = new LinkedHashMap<>();
		for (Map.Entry<Item, Long> entry : entries) {
			ordered.put(entry.getKey(), entry.getValue());
		}

		// 调试日志：显示识别到的币种面额映射
		System.out.println("[ConversionService] Denominations recognized (relative to " + configured.toString() + "):");
		for (Map.Entry<Item, Long> entry : ordered.entrySet()) {
			System.out.println("  " + entry.getKey().toString() + " -> " + entry.getValue());
		}

		return ordered;
	}
}
