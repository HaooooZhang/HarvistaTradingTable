package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.Config;
import ink.myumoon.tradingtable.menu.TradingTableTradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
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
    private boolean awaitingTradeResult;
    private int pendingTradeEventId;

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
        this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.button.execute"), b -> this.handleTradeClick())
                .bounds(executeX, executeY, executeWidth, 20)
                .build());

        int amountY = this.topPos + AMOUNT_BUTTON_Y;
        this.amountMinusButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.button.amount_decrease"), b -> sendButton(TradingTableTradeMenu.BUTTON_AMOUNT_MINUS))
                .bounds(this.leftPos + 8, amountY, 20, 20)
                .build());
        this.amountPlusButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.button.amount_increase"), b -> sendButton(TradingTableTradeMenu.BUTTON_AMOUNT_PLUS))
                .bounds(this.leftPos + 48, amountY, 20, 20)
                .build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.handleStepClick(this.amountPlusButton, mouseX, mouseY, button)) {
            return true;
        }
        if (this.handleStepClick(this.amountMinusButton, mouseX, mouseY, button)) {
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

    private boolean handleStepClick(Button buttonWidget, double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 || buttonWidget == null || !buttonWidget.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        boolean isMinus = buttonWidget == this.amountMinusButton;
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
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Component header = Component.translatable("ui.trading_table.trade.header", this.title, Component.translatable("container.trading_table.trade"));
        guiGraphics.drawString(this.font, header, this.leftPos + PANEL_PADDING, this.topPos + HEADER_Y, COLOR_TEXT, false);

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

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderTradeItem(GuiGraphics guiGraphics) {
        int boxLeft = this.leftPos + ICON_BOX_X;
        int boxTop = this.topPos + ICON_BOX_Y;

        Item tradeItem = this.menu.getTradeItem();
        if (tradeItem == null) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("ui.trading_table.trade.none"),
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
        guiGraphics.renderItem(new ItemStack(Config.getVanillaCurrencyItem()), currencyIconX, priceY - 4);
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





