package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.trade.TaxService;
import ink.myumoon.tradingtable.menu.TradingTableTradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class TradingTableTradeScreen extends AbstractContainerScreen<TradingTableTradeMenu> {
    private static final ResourceLocation TRADE_BG_TEXTURE = ResourceLocation.fromNamespaceAndPath("trading_table", "textures/gui/tradingtable_trade.png");
    private static final int BG_TEXTURE_WIDTH = 256;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int COLOR_TEXT = 0x404040;
    private static final int PANEL_PADDING = 8;
    private static final int HEADER_Y = 6;
    private static final int ICON_BOX_X = 20;
    private static final int ICON_BOX_Y = 25;
    private static final int ICON_BOX_SIZE = 36;
    private static final int ICON_RENDER_OFFSET = 6;
    private static final int ICON_RENDER_SIZE = 24;
    private static final int INFO_X = 78;
    private static final int INFO_START_Y = 25;
    private static final int INFO_ROW_STEP = 14;
    private static final int AMOUNT_CENTER_X = ICON_BOX_X + ICON_BOX_SIZE / 2;
    private static final int AMOUNT_VALUE_Y = 75;
    private static final int AMOUNT_BUTTON_Y = 70;
    private static final int PLAYER_INV_LABEL_Y = 96;

    private Button amountPlusButton;
    private Button amountMinusButton;
    private Button executeButton;
    private boolean awaitingTradeResult;
    private int pendingTradeEventId;
    private long headerScrollTime;

    public TradingTableTradeScreen(TradingTableTradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();

        int executeWidth = 80;
        int panelWidth = this.imageWidth - PANEL_PADDING * 2;
        int executeX = this.leftPos + 78;
        int executeY = this.topPos + 70;

        Tooltip stepTooltip = Tooltip.create(Component.translatable("ui.trading_table.step.tooltip"));

        int amountY = this.topPos + AMOUNT_BUTTON_Y;
        this.amountMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(TradingTableTradeMenu.BUTTON_AMOUNT_MINUS))
                .bounds(this.leftPos + 8, amountY, 20, 20)
                .tooltip(stepTooltip)
                .build());
        this.amountPlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(TradingTableTradeMenu.BUTTON_AMOUNT_PLUS))
                .bounds(this.leftPos + 48, amountY, 20, 20)
                .tooltip(stepTooltip)
                .build());

        // 动态 Tooltip 不能在 init 时静态创建 —— 保存按钮引用，在 render 时根据当前数值动态显示提示
        this.executeButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.button.execute"), b -> this.handleTradeClick())
                .bounds(executeX, executeY, executeWidth, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int amountCenterX = this.leftPos + AMOUNT_CENTER_X;
            if (mouseX >= amountCenterX - 20 && mouseX <= amountCenterX + 20 && mouseY >= this.topPos + AMOUNT_VALUE_Y && mouseY <= this.topPos + AMOUNT_VALUE_Y + 18) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, false, true);
                else this.handleStepClick(null, mouseX, mouseY, 0, true, true);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.handleStepClick(this.amountPlusButton, mouseX, mouseY, button, false, false)) {
            return true;
        }
        if (this.handleStepClick(this.amountMinusButton, mouseX, mouseY, button, true, false)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void handleTradeClick() {
        if (this.awaitingTradeResult) {
            return;
        }
        this.pendingTradeEventId = this.menu.getTradeEventId();
        this.awaitingTradeResult = true;
        this.sendButton(TradingTableTradeMenu.BUTTON_EXECUTE);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!this.awaitingTradeResult) {
            return;
        }

        int currentTradeEventId = this.menu.getTradeEventId();
        if (currentTradeEventId == this.pendingTradeEventId) {
            return;
        }

        this.awaitingTradeResult = false;
        this.playTradeResultSound(this.menu.wasLastTradeSuccessful());
        // Service has finished and pushed a new event id (success or failure): close the GUI.
        this.onClose();
    }

    private boolean handleStepClick(Button buttonWidget, double mouseX, double mouseY, int mouseButton, boolean isMinus, boolean bypassButtonCheck) {
        if (!bypassButtonCheck && (mouseButton != 0 || buttonWidget == null || !buttonWidget.isMouseOver(mouseX, mouseY))) {
            return false;
        }

        int oneId = isMinus ? TradingTableTradeMenu.BUTTON_AMOUNT_MINUS : TradingTableTradeMenu.BUTTON_AMOUNT_PLUS;
        int eightId = isMinus ? TradingTableTradeMenu.BUTTON_AMOUNT_MINUS_8 : TradingTableTradeMenu.BUTTON_AMOUNT_PLUS_8;
        int thirtyTwoId = isMinus ? TradingTableTradeMenu.BUTTON_AMOUNT_MINUS_32 : TradingTableTradeMenu.BUTTON_AMOUNT_PLUS_32;

        if (Screen.hasControlDown()) {
            this.playStepButtonSound();
            this.sendButton(thirtyTwoId);
            return true;
        }

        if (Screen.hasShiftDown()) {
            this.playStepButtonSound();
            this.sendButton(eightId);
            return true;
        }

        this.playStepButtonSound();
        this.sendButton(oneId);
        return true;
    }

    private void playStepButtonSound() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void playTradeResultSound(boolean success) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (success) {
            this.minecraft.player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.0F);
        } else {
            this.minecraft.player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.9F, 1.0F);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        //this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Component header = Component.translatable("ui.trading_table.trade.header", this.title, Component.translatable("container.trading_table.trade"));
        int headerWidth = this.font.width(header);
        if (headerWidth > 160) {
            long time = net.minecraft.Util.getMillis();
            if (this.headerScrollTime == 0) {
                this.headerScrollTime = time;
            }
            long delta = time - this.headerScrollTime;
            long pauseDuration = 1800L; // 3 seconds pause

            int scroll = 0;
            if (delta > pauseDuration) {
                scroll = (int) ((delta - pauseDuration) / 30L);
            }

            int maxScroll = headerWidth - 160;
            if (scroll > maxScroll + 60) { // 60 frames extra pause at the end
                this.headerScrollTime = time;
                scroll = 0;
            } else if (scroll > maxScroll) {
                scroll = maxScroll;
            }
            guiGraphics.enableScissor(this.leftPos + PANEL_PADDING, this.topPos + HEADER_Y, this.leftPos + PANEL_PADDING + 160, this.topPos + HEADER_Y + 10);
            guiGraphics.drawString(this.font, header, this.leftPos + PANEL_PADDING - scroll, this.topPos + HEADER_Y, COLOR_TEXT, false);
            guiGraphics.disableScissor();
        } else {
            guiGraphics.drawString(this.font, header, this.leftPos + PANEL_PADDING, this.topPos + HEADER_Y, COLOR_TEXT, false);
        }

        this.renderTradeItem(guiGraphics);
        this.renderTradeInfo(guiGraphics);

        int amountCenterX = this.leftPos + AMOUNT_CENTER_X;
        Component amountText = Component.literal(Integer.toString(this.menu.getRequestedAmount()))
                .withStyle(style -> style.withUnderlined(true));
        int amountX = amountCenterX - this.font.width(amountText) / 2;
        guiGraphics.drawString(this.font, amountText, amountX, this.topPos + AMOUNT_VALUE_Y, COLOR_TEXT, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.leftPos + PANEL_PADDING, this.topPos + PLAYER_INV_LABEL_Y, COLOR_TEXT, false);

        Item tradeItem = this.menu.getTradeItem();
        if (tradeItem != null && this.isMouseOverTradeItemIcon(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, new ItemStack(tradeItem), mouseX, mouseY);
        }

        // 动态税收提示：按钮 Tooltip 不能在 init() 时静态创建，因为数量/价格会变。
        long taxAmount = TaxService.calculateTax(this.menu.getUnitPrice() * (double) this.menu.getRequestedAmount());
        if (this.executeButton != null && this.executeButton.isMouseOver(mouseX, mouseY) && taxAmount > 0L && this.menu.isBuyOrder()) {
            Component taxComp = Component.translatable("ui.trading_table.tax.tooltip", (int)(Config.getTaxRate() * 100), taxAmount, I18n.get(Config.getCurrencyItem().getDescriptionId()));
            java.util.List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(taxComp, 180);
            guiGraphics.renderTooltip(this.font, lines, mouseX, mouseY);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderTradeItem(GuiGraphics guiGraphics) {
        int boxLeft = this.leftPos + ICON_BOX_X;
        int boxTop = this.topPos + ICON_BOX_Y;

        Item tradeItem = this.menu.getTradeItem();
        if (tradeItem == null) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal("-"),
                    boxLeft + ICON_BOX_SIZE / 2,
                    boxTop + 14,
                    COLOR_TEXT);
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(boxLeft + ICON_RENDER_OFFSET, boxTop + ICON_RENDER_OFFSET, 0.0F);
        guiGraphics.pose().scale(1.5F, 1.5F, 1.0F);
        guiGraphics.renderItem(new ItemStack(tradeItem), 0, 0);
        guiGraphics.pose().popPose();
    }

    private boolean isMouseOverTradeItemIcon(double mouseX, double mouseY) {
        int iconX = this.leftPos + ICON_BOX_X + ICON_RENDER_OFFSET;
        int iconY = this.topPos + ICON_BOX_Y + ICON_RENDER_OFFSET;
        return mouseX >= iconX
                && mouseX < iconX + ICON_RENDER_SIZE
                && mouseY >= iconY
                && mouseY < iconY + ICON_RENDER_SIZE;
    }

    private void renderTradeInfo(GuiGraphics guiGraphics) {
        Component tradeType = Component.translatable(this.menu.isBuyOrder()
                ? "ui.trading_table.trade.type.buy"
                : "ui.trading_table.trade.type.sell");

        guiGraphics.drawString(this.font,
                Component.translatable("ui.trading_table.trade.line.type", tradeType),
                this.leftPos + INFO_X,
                this.topPos + INFO_START_Y,
                COLOR_TEXT,
                false);

        int priceY = this.topPos + INFO_START_Y + INFO_ROW_STEP;
        Component priceText = Component.translatable("ui.trading_table.trade.line.price", this.menu.getUnitPrice());
        guiGraphics.drawString(this.font,
                priceText,
                this.leftPos + INFO_X,
                priceY,
                COLOR_TEXT,
                false);
        int currencyIconX = this.leftPos + INFO_X + this.font.width(priceText) + 3;
        guiGraphics.renderItem(new ItemStack(Config.getCurrencyItem()), currencyIconX, priceY - 4);
        guiGraphics.drawString(this.font,
                Component.translatable("ui.trading_table.trade.line.min_suffix", this.menu.getMinTradeAmount()),
                currencyIconX + 18,
                priceY,
                COLOR_TEXT,
                false);

        if (this.menu.isBuyOrder()) {
            double balance = this.menu.getCurrencyBalance();
            guiGraphics.drawString(this.font,
                    Component.translatable("ui.trading_table.trade.line.balance", String.format(Locale.ROOT, "%.1f", balance)),
                    this.leftPos + INFO_X,
                    this.topPos + INFO_START_Y + INFO_ROW_STEP * 2,
                    COLOR_TEXT,
                    false);
            return;
        }

        guiGraphics.drawString(this.font,
                Component.translatable("ui.trading_table.trade.line.stock", this.menu.getStockCount()),
                this.leftPos + INFO_X,
                this.topPos + INFO_START_Y + INFO_ROW_STEP * 2,
                COLOR_TEXT,
                false);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // All text is drawn in render() with custom layout.
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TRADE_BG_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);
    }
}

