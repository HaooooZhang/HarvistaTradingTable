package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.economy.TaxService;
import ink.myumoon.tradingtable.menu.TradingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class TradingTableScreen extends AbstractContainerScreen<TradingTableMenu> {
    private static final ResourceLocation MANAGE_BG_TEXTURE = ResourceLocation.fromNamespaceAndPath("trading_table", "textures/gui/tradingtable_manger.png");
    private static final int BG_TEXTURE_WIDTH = 512;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int COLOR_TEXT = 0x404040;
    private static final int PANEL_LEFT_X = 8;
    private static final int PANEL_RIGHT_X = 252;

    private Button minPlusButton;
    private Button minMinusButton;
    private Button pricePlusButton;
    private Button priceMinusButton;
    private Button typeSellButton;
    private Button typeBuyButton;
    private Button enableToggleButton;
    private Button extractButton;
    private Button confirmTradeItemButton;
    private Button saveButton;
    private EditBox tableNameBox;
    private String savedTableNameBaseline = "";
    private long headerScrollTime;

    public TradingTableScreen(TradingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 336;
        this.imageHeight = 166;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();

        int leftX = this.leftPos + PANEL_LEFT_X;
        int topY = this.topPos + 10;
        this.typeSellButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.type.sell"), b -> sendButton(TradingTableMenu.BUTTON_SET_TYPE_SELL))
                .bounds(leftX, topY + 16, 36, 20)
                .build());
        this.typeBuyButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.type.buy"), b -> sendButton(TradingTableMenu.BUTTON_SET_TYPE_BUY))
                .bounds(leftX + 40, topY + 16, 36, 20)
                .build());

        Tooltip stepTooltip = Tooltip.create(Component.translatable("ui.trading_table.step.tooltip"));

        this.priceMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(TradingTableMenu.BUTTON_PRICE_MINUS))
                .bounds(leftX, topY + 50 , 20, 20)
                .tooltip(stepTooltip)
                .build());
        this.pricePlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(TradingTableMenu.BUTTON_PRICE_PLUS))
                .bounds(leftX + 56, topY + 50, 20, 20)
                .tooltip(stepTooltip)
                .build());

        this.minMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(TradingTableMenu.BUTTON_MIN_MINUS))
                .bounds(leftX, topY + 84, 20, 20)
                .tooltip(stepTooltip)
                .build());
        this.minPlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(TradingTableMenu.BUTTON_MIN_PLUS))
                .bounds(leftX + 56, topY + 84, 20, 20)
                .tooltip(stepTooltip)
                .build());

        this.enableToggleButton = this.addRenderableWidget(Button.builder(resolveEnableLabel(), b -> sendButton(TradingTableMenu.BUTTON_TOGGLE_ENABLED))
                .bounds(leftX, topY + 108, 76, 20)
                .build());


        int rightX = this.leftPos + PANEL_RIGHT_X;
        this.tableNameBox = new EditBox(this.font, rightX, this.topPos + 26, 76, 18, Component.translatable("ui.trading_table.manage.name"));
        this.tableNameBox.setMaxLength(TradingTableBlockEntity.MAX_TABLE_NAME_LENGTH);
        this.tableNameBox.setCanLoseFocus(true);
        this.savedTableNameBaseline = TradingTableBlockEntity.sanitizeTableName(extractInitialTableName());
        this.tableNameBox.setValue(this.savedTableNameBaseline);
        this.tableNameBox.setEditable(this.menu.isAllowManage());
        this.tableNameBox.setFocused(false);
        this.addWidget(this.tableNameBox);

        this.confirmTradeItemButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.trade_item.confirm"), b -> sendButton(TradingTableMenu.BUTTON_CONFIRM_TRADE_ITEM))
                .bounds(rightX + 34, this.topPos + 58, 36, 20)
                .build());
        this.extractButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.extract"), b -> sendButton(TradingTableMenu.BUTTON_EXTRACT))
                .bounds(rightX, this.topPos + 106, 76, 20)
                .build());
        this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.save"), b -> sendSaveWithTableName())
                .bounds(rightX, this.topPos + 130, 76, 20)
                .build());

        this.updateStateButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int leftX = this.leftPos + PANEL_LEFT_X;
            int topY = this.topPos + 10;
            if (mouseX >= leftX + 20 && mouseX <= leftX + 56 && mouseY >= topY + 50 && mouseY <= topY + 70) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, TradingTableMenu.BUTTON_PRICE_PLUS, TradingTableMenu.BUTTON_PRICE_PLUS_8, TradingTableMenu.BUTTON_PRICE_PLUS_32, true);
                else this.handleStepClick(null, mouseX, mouseY, 0, TradingTableMenu.BUTTON_PRICE_MINUS, TradingTableMenu.BUTTON_PRICE_MINUS_8, TradingTableMenu.BUTTON_PRICE_MINUS_32, true);
                return true;
            }
            if (mouseX >= leftX + 20 && mouseX <= leftX + 56 && mouseY >= topY + 84 && mouseY <= topY + 104) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, TradingTableMenu.BUTTON_MIN_PLUS, TradingTableMenu.BUTTON_MIN_PLUS_8, TradingTableMenu.BUTTON_MIN_PLUS_32, true);
                else this.handleStepClick(null, mouseX, mouseY, 0, TradingTableMenu.BUTTON_MIN_MINUS, TradingTableMenu.BUTTON_MIN_MINUS_8, TradingTableMenu.BUTTON_MIN_MINUS_32, true);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.tableNameBox != null && this.tableNameBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.tableNameBox);
            return true;
        }
        if (this.handleStepClick(this.minPlusButton, mouseX, mouseY, button,
                TradingTableMenu.BUTTON_MIN_PLUS, TradingTableMenu.BUTTON_MIN_PLUS_8, TradingTableMenu.BUTTON_MIN_PLUS_32, false)) {
            return true;
        }
        if (this.handleStepClick(this.minMinusButton, mouseX, mouseY, button,
                TradingTableMenu.BUTTON_MIN_MINUS, TradingTableMenu.BUTTON_MIN_MINUS_8, TradingTableMenu.BUTTON_MIN_MINUS_32, false)) {
            return true;
        }
        if (this.handleStepClick(this.pricePlusButton, mouseX, mouseY, button,
                TradingTableMenu.BUTTON_PRICE_PLUS, TradingTableMenu.BUTTON_PRICE_PLUS_8, TradingTableMenu.BUTTON_PRICE_PLUS_32, false)) {
            return true;
        }
        if (this.handleStepClick(this.priceMinusButton, mouseX, mouseY, button,
                TradingTableMenu.BUTTON_PRICE_MINUS, TradingTableMenu.BUTTON_PRICE_MINUS_8, TradingTableMenu.BUTTON_PRICE_MINUS_32, false)) {
            return true;
        }
        if (this.handleExtractClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.tableNameBox != null && this.tableNameBox.isFocused() && this.tableNameBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.tableNameBox != null && this.tableNameBox.isFocused() && this.minecraft != null
                && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.tableNameBox != null && this.tableNameBox.isFocused() && this.tableNameBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.updateStateButtons();
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void sendSaveWithTableName() {
        if (!this.menu.isAllowManage()) {
            return;
        }

        String value = this.tableNameBox == null ? "" : this.tableNameBox.getValue();
        this.sendButton(TradingTableMenu.BUTTON_NAME_CLEAR);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            this.sendButton(TradingTableMenu.BUTTON_NAME_APPEND_HIGH_BASE + ((ch >>> 8) & 0xFF));
            this.sendButton(TradingTableMenu.BUTTON_NAME_APPEND_LOW_BASE + (ch & 0xFF));
        }
        this.sendButton(TradingTableMenu.BUTTON_SAVE);
        this.savedTableNameBaseline = TradingTableBlockEntity.sanitizeTableName(value);
    }

    private boolean handleStepClick(Button buttonWidget, double mouseX, double mouseY, int mouseButton,
                                    int oneId, int eightId, int thirtyTwoId, boolean bypassButtonCheck) {
        if (!bypassButtonCheck && (mouseButton != 0 || buttonWidget == null || !buttonWidget.isMouseOver(mouseX, mouseY))) {
            return false;
        }

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


    private boolean handleExtractClick(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 || this.extractButton == null || !this.extractButton.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (Screen.hasControlDown()) {
            this.playStepButtonSound();
            this.sendButton(TradingTableMenu.BUTTON_EXTRACT_ALL);
            return true;
        }
        if (Screen.hasShiftDown()) {
            this.playStepButtonSound();
            this.sendButton(TradingTableMenu.BUTTON_EXTRACT_STACK);
            return true;
        }
        this.playStepButtonSound();
        this.sendButton(TradingTableMenu.BUTTON_EXTRACT);
        return true;
    }

    private void updateStateButtons() {
        boolean allowManage = this.menu.isAllowManage();
        if (this.typeSellButton != null) {
            this.typeSellButton.active = allowManage && this.menu.isBuyOrder();
        }
        if (this.typeBuyButton != null) {
            this.typeBuyButton.active = allowManage && !this.menu.isBuyOrder();
        }
        if (this.enableToggleButton != null) {
            this.enableToggleButton.active = allowManage;
            this.enableToggleButton.setMessage(resolveEnableLabel());
        }
        if (this.extractButton != null) {
            this.extractButton.active = allowManage;
        }
        if (this.confirmTradeItemButton != null) {
            this.confirmTradeItemButton.active = allowManage && this.menu.isTradeItemSelectionDirty() && this.menu.getSlot(0).hasItem();
        }
        if (this.saveButton != null) {
            this.saveButton.active = allowManage && (this.menu.hasUnsavedManageChanges() || this.isTableNameDirty());
        }
    }

    private boolean isTableNameDirty() {
        String current = this.tableNameBox == null ? "" : TradingTableBlockEntity.sanitizeTableName(this.tableNameBox.getValue());
        return !current.equals(this.savedTableNameBaseline);
    }

    private Component resolveEnableLabel() {
        return Component.translatable(this.menu.isEnabled()
                ? "ui.trading_table.manage.disable"
                : "ui.trading_table.manage.enable");
    }

    private String extractInitialTableName() {
        String rawTitle = this.title.getString();
        int separatorIndex = rawTitle.indexOf('|');
        if (separatorIndex >= 0) {
            return rawTitle.substring(0, separatorIndex).strip();
        }
        return rawTitle;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        //this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Component header = Component.translatable(
                "ui.trading_table.trade.header",
                Component.literal(this.tableNameBox == null || this.tableNameBox.getValue().isBlank() ? this.title.getString() : this.tableNameBox.getValue()),
                Component.translatable("container.trading_table.manage")
        );

        int headerWidth = this.font.width(header);
        if (headerWidth > 160) {
            long time = net.minecraft.Util.getMillis();
            if (this.headerScrollTime == 0) {
                this.headerScrollTime = time;
            }
            long delta = time - this.headerScrollTime;
            long pauseDuration = 1800; // 3 seconds pause

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
            guiGraphics.enableScissor(this.leftPos + 88, this.topPos + 6, this.leftPos + 88 + 160, this.topPos + 6 + 10);
            guiGraphics.drawString(this.font, header, this.leftPos + 88 - scroll, this.topPos + 6, COLOR_TEXT, false);
            guiGraphics.disableScissor();
        } else {
            guiGraphics.drawString(this.font, header, this.leftPos + 88, this.topPos + 6, COLOR_TEXT, false);
        }

        int leftX = this.leftPos + PANEL_LEFT_X;
        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.manage.type"), leftX, this.topPos + 16, COLOR_TEXT, false);
        this.drawAdjustBlock(guiGraphics, Component.translatable("ui.trading_table.manage.price"), Integer.toString(this.menu.getUnitPrice()), leftX, this.topPos + 50);
        this.drawAdjustBlock(guiGraphics, Component.translatable("ui.trading_table.manage.min"), Integer.toString(this.menu.getMinTradeAmount()), leftX, this.topPos + 84);

        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.leftPos + 88, this.topPos + 73, COLOR_TEXT, false);

        int rightX = this.leftPos + PANEL_RIGHT_X;
        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.manage.name"), rightX, this.topPos + 16, COLOR_TEXT, false);
        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.manage.trade_item"), rightX, this.topPos + 48, COLOR_TEXT, false);

        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.manage.currency"), rightX, this.topPos + 82, COLOR_TEXT, false);
        String balance = String.format(Locale.ROOT, "%.1f", this.menu.getCashierBalance());
        guiGraphics.drawString(this.font, balance, rightX + 8, this.topPos + 94, COLOR_TEXT, false);
        Item currencyItem = Config.getCurrencyItem();
        guiGraphics.renderItem(new ItemStack(currencyItem), rightX + this.font.width(balance) + 2 + 8, this.topPos + 90);

        if (this.tableNameBox != null) {
            this.tableNameBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawAdjustBlock(GuiGraphics guiGraphics, Component label, String value, int x, int y) {
        guiGraphics.drawString(this.font, label, x, y, COLOR_TEXT, false);
        Component underlined = Component.literal(value).withStyle(style -> style.withUnderlined(true));
        int centerX = x + 42 - 4;
        guiGraphics.drawString(this.font, underlined, centerX - this.font.width(underlined) / 2, y + 16, COLOR_TEXT, false);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Labels are fully custom-rendered in render().
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(MANAGE_BG_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);
    }
}

