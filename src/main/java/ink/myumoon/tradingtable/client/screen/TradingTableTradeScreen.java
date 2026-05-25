package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.economy.NeoEssentialsEconomyBackend;
import ink.myumoon.tradingtable.menu.TradingTableInitMenu;
import ink.myumoon.tradingtable.trade.TaxService;
import ink.myumoon.tradingtable.menu.TradingTableTradeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class TradingTableTradeScreen extends AbstractContainerScreen<TradingTableTradeMenu> {
    private static final Identifier TRADE_BG_TEXTURE = Identifier.fromNamespaceAndPath("trading_table", "textures/gui/tradingtable_trade.png");
    private static final int BG_TEXTURE_WIDTH = 256;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int COLOR_TEXT = 0xFF404040;
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
        super(menu, playerInventory, title, 176, 190);
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
                int mods = (this.minecraft != null && this.minecraft.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                         | (this.minecraft != null && this.minecraft.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0);
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, false, true, mods);
                else this.handleStepClick(null, mouseX, mouseY, 0, true, true, mods);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (this.handleStepClick(this.amountPlusButton, mx, my, button, false, false, event.modifiers())) {
            return true;
        }
        if (this.handleStepClick(this.amountMinusButton, mx, my, button, true, false, event.modifiers())) {
            return true;
        }
        return super.mouseClicked(event, flag);
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

    private void doTick() {
        super.tick();
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

    private boolean handleStepClick(Button buttonWidget, double mouseX, double mouseY, int mouseButton,
                                    boolean isMinus, boolean bypassButtonCheck, int modifiers) {
        if (!bypassButtonCheck && (mouseButton != 0 || buttonWidget == null || !buttonWidget.isMouseOver(mouseX, mouseY))) {
            return false;
        }

        int oneId = isMinus ? TradingTableTradeMenu.BUTTON_AMOUNT_MINUS : TradingTableTradeMenu.BUTTON_AMOUNT_PLUS;
        int eightId = isMinus ? TradingTableTradeMenu.BUTTON_AMOUNT_MINUS_8 : TradingTableTradeMenu.BUTTON_AMOUNT_PLUS_8;
        int thirtyTwoId = isMinus ? TradingTableTradeMenu.BUTTON_AMOUNT_MINUS_32 : TradingTableTradeMenu.BUTTON_AMOUNT_PLUS_32;

        if (this.minecraft != null && this.minecraft.hasControlDown()) {
            this.playStepButtonSound();
            this.sendButton(thirtyTwoId);
            return true;
        }

        if (this.minecraft != null && this.minecraft.hasShiftDown()) {
            this.playStepButtonSound();
            this.sendButton(eightId);
            return true;
        }

        this.playStepButtonSound();
        this.sendButton(oneId);
        return true;
    }

    // mouseScrolled has no modifiers param, use GLFW directly

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
            this.minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 1.0F);
        } else {
            this.minecraft.player.playSound(SoundEvents.DISPENSER_FAIL, 0.9F, 1.0F);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        doTick();

        Component header = Component.translatable("ui.trading_table.trade.header", this.title, Component.translatable("container.trading_table.trade"));
        int headerWidth = getFont().width(header);
        if (headerWidth > 160) {
            long time = System.currentTimeMillis();
            if (this.headerScrollTime == 0) {
                this.headerScrollTime = time;
            }
            long delta = time - this.headerScrollTime;
            long pauseDuration = 1800L;

            int scroll = 0;
            if (delta > pauseDuration) {
                scroll = (int) ((delta - pauseDuration) / 30L);
            }

            int maxScroll = headerWidth - 160;
            if (scroll > maxScroll + 60) {
                this.headerScrollTime = time;
                scroll = 0;
            } else if (scroll > maxScroll) {
                scroll = maxScroll;
            }
            guiGraphics.enableScissor(this.leftPos + PANEL_PADDING, this.topPos + HEADER_Y, this.leftPos + PANEL_PADDING + 160, this.topPos + HEADER_Y + 10);
            guiGraphics.text(getFont(), header, this.leftPos + PANEL_PADDING - scroll, this.topPos + HEADER_Y, COLOR_TEXT, false);
            guiGraphics.disableScissor();
        } else {
            guiGraphics.text(getFont(), header, this.leftPos + PANEL_PADDING, this.topPos + HEADER_Y, COLOR_TEXT, false);
        }

        this.renderTradeItem(guiGraphics);
        this.renderTradeInfo(guiGraphics);

        int amountCenterX = this.leftPos + AMOUNT_CENTER_X;
        Component amountText = Component.literal(Integer.toString(this.menu.getRequestedAmount()))
                .withStyle(style -> style.withUnderlined(true));
        int amountX = amountCenterX - getFont().width(amountText) / 2;
        guiGraphics.text(getFont(), amountText, amountX, this.topPos + AMOUNT_VALUE_Y, COLOR_TEXT, false);
        guiGraphics.text(getFont(), this.playerInventoryTitle, this.leftPos + PANEL_PADDING, this.topPos + PLAYER_INV_LABEL_Y, COLOR_TEXT, false);

        Item tradeItem = this.menu.getTradeItem();
        if (tradeItem != null && this.isMouseOverTradeItemIcon(mouseX, mouseY)) {
            guiGraphics.setTooltipForNextFrame(getFont(), new ItemStack(tradeItem), mouseX, mouseY);
        }

        double taxAmount = TaxService.calculateTax(this.menu.getUnitPrice() * (double) this.menu.getRequestedAmount());
        if (this.executeButton != null && this.executeButton.isMouseOver(mouseX, mouseY) && taxAmount > 0.0D && this.menu.isBuyOrder()) {
            String displayTax;
            if (Config.isNeoEssentialsMode()){
                displayTax = String.format("%.2f", taxAmount);
            }else{
                displayTax = String.format("%s",(long)taxAmount);
            }
            String currencyName = Config.isNeoEssentialsMode()
                    ? NeoEssentialsEconomyBackend.getCurrencySymbol()
                    : I18n.get(Config.getCurrencyItem().getDescriptionId());
            Component taxComp = Component.translatable("ui.trading_table.tax.tooltip", (int)(Config.getTaxRate() * 100), displayTax, currencyName);
            java.util.List<net.minecraft.util.FormattedCharSequence> lines = getFont().split(taxComp, 180);
            guiGraphics.setTooltipForNextFrame(getFont(), lines, mouseX, mouseY);
        }
    }

    private void renderTradeItem(GuiGraphicsExtractor g) {
        int boxLeft = this.leftPos + ICON_BOX_X;
        int boxTop = this.topPos + ICON_BOX_Y;

        Item tradeItem = this.menu.getTradeItem();
        if (tradeItem == null) {
            g.centeredText(getFont(),
                    Component.literal("-"),
                    boxLeft + ICON_BOX_SIZE / 2,
                    boxTop + 14,
                    COLOR_TEXT);
            return;
        }

        g.pose().pushMatrix();
        g.pose().translate(boxLeft + ICON_RENDER_OFFSET, boxTop + ICON_RENDER_OFFSET);
        g.pose().scale(1.5F, 1.5F);
        g.item(new ItemStack(tradeItem), 0, 0);
        g.pose().popMatrix();
    }

    private boolean isMouseOverTradeItemIcon(double mouseX, double mouseY) {
        int iconX = this.leftPos + ICON_BOX_X + ICON_RENDER_OFFSET;
        int iconY = this.topPos + ICON_BOX_Y + ICON_RENDER_OFFSET;
        return mouseX >= iconX
                && mouseX < iconX + ICON_RENDER_SIZE
                && mouseY >= iconY
                && mouseY < iconY + ICON_RENDER_SIZE;
    }

    private void renderTradeInfo(GuiGraphicsExtractor g) {
        Component tradeType = Component.translatable(this.menu.isBuyOrder()
                ? "ui.trading_table.trade.type.buy"
                : "ui.trading_table.trade.type.sell");

        g.text(getFont(),
                Component.translatable("ui.trading_table.trade.line.type", tradeType),
                this.leftPos + INFO_X,
                this.topPos + INFO_START_Y,
                COLOR_TEXT, false);

        int priceY = this.topPos + INFO_START_Y + INFO_ROW_STEP;
        Component priceText = Component.translatable("ui.trading_table.trade.line.price", this.menu.getUnitPrice());
        g.text(getFont(),
                priceText,
                this.leftPos + INFO_X,
                priceY,
                COLOR_TEXT, false);
        int currencyIconX = this.leftPos + INFO_X + getFont().width(priceText) + 3;
        if (Config.isNeoEssentialsMode()) {
            String symbol = NeoEssentialsEconomyBackend.getCurrencySymbol();
            g.text(getFont(), symbol, currencyIconX, priceY, COLOR_TEXT, false);
            g.text(getFont(),
                    Component.translatable("ui.trading_table.trade.line.min_suffix", this.menu.getMinTradeAmount()),
                    currencyIconX + getFont().width(NeoEssentialsEconomyBackend.getCurrencySymbol()) + 4,
                    priceY,
                    COLOR_TEXT, false);
        } else {
            g.item(new ItemStack(Config.getCurrencyItem()), currencyIconX, priceY - 4);
            g.text(getFont(),
                    Component.translatable("ui.trading_table.trade.line.min_suffix", this.menu.getMinTradeAmount()),
                    currencyIconX + 18,
                    priceY,
                    COLOR_TEXT, false);
        }

        if (this.menu.isBuyOrder()) {
            double balance = this.menu.getCurrencyBalance();
            g.text(getFont(),
                    Component.translatable("ui.trading_table.trade.line.balance", String.format(Locale.ROOT, "%.1f", balance)),
                    this.leftPos + INFO_X,
                    this.topPos + INFO_START_Y + INFO_ROW_STEP * 2,
                    COLOR_TEXT, false);
            return;
        }

        g.text(getFont(),
                Component.translatable("ui.trading_table.trade.line.stock", this.menu.getStockCount()),
                this.leftPos + INFO_X,
                this.topPos + INFO_START_Y + INFO_ROW_STEP * 2,
                COLOR_TEXT, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, TRADE_BG_TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);
        super.extractContents(g, mouseX, mouseY, partialTick);
    }
}

