package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.blockentity.SystemTradingTableBlockEntity;
import ink.myumoon.tradingtable.menu.SystemTradingTableMenu;
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
import org.jetbrains.annotations.NotNull;

public class SystemTradingTableScreen extends AbstractContainerScreen<SystemTradingTableMenu> {
    private static final ResourceLocation MANAGE_BG_TEXTURE = ResourceLocation.fromNamespaceAndPath("trading_table", "textures/gui/systemtable_manger.png");
    private static final int BG_TEXTURE_WIDTH = 256;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int COLOR_TEXT = 0x404040;
    private static final int LEFT_PANEL_X = 8;
    private static final int RIGHT_PANEL_X = 88;
    private static final int NAME_BOX_Y = 16;
    private static final int TYPE_BUTTON_Y = 92;
    private static final int PRICE_ROW_Y = 16;
    private static final int MIN_ROW_Y = 48;
    private static final int ENABLE_BUTTON_Y = 82;
    private static final int SAVE_BUTTON_Y = 104;
    private static final int ROW_LABEL_OFFSET = 10;
    private static final int STEP_BUTTON_WIDTH = 20;
    private static final int STEP_BUTTON_HEIGHT = 20;
    private static final int TYPE_BUTTON_WIDTH = 36;
    private static final int TYPE_BUTTON_HEIGHT = 20;
    private static final int TYPE_BUTTON_GAP = 4;
    private static final int NAME_BOX_WIDTH = 76;
    private static final int NAME_BOX_HEIGHT = 18;

    private Button typeSellButton;
    private Button typeBuyButton;
    private Button minPlusButton;
    private Button pricePlusButton;
    private Button minMinusButton;
    private Button priceMinusButton;
    private Button enableToggleButton;
    private Button confirmTradeItemButton;
    private Button saveButton;
    private EditBox tableNameBox;
    private String savedTableNameBaseline = "";
    private long headerScrollTime;

    public SystemTradingTableScreen(SystemTradingTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 210;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();

        int leftX = this.leftPos + LEFT_PANEL_X;
        int rightX = this.leftPos + RIGHT_PANEL_X;

        this.tableNameBox = new EditBox(this.font, leftX, this.topPos + NAME_BOX_Y + ROW_LABEL_OFFSET, NAME_BOX_WIDTH, NAME_BOX_HEIGHT, Component.translatable("ui.trading_table.manage.name"));
        this.tableNameBox.setMaxLength(SystemTradingTableBlockEntity.MAX_TABLE_NAME_LENGTH);
        this.tableNameBox.setCanLoseFocus(true);
        this.savedTableNameBaseline = SystemTradingTableBlockEntity.sanitizeTableName(extractInitialTableName());
        this.tableNameBox.setValue(this.savedTableNameBaseline);
        this.tableNameBox.setFocused(false);
        this.addWidget(this.tableNameBox);

        this.typeSellButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.type.sell"), b -> sendButton(SystemTradingTableMenu.BUTTON_SET_TYPE_SELL))
                .bounds(leftX, this.topPos + TYPE_BUTTON_Y, TYPE_BUTTON_WIDTH, TYPE_BUTTON_HEIGHT)
                .build());
        this.typeBuyButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.type.buy"), b -> sendButton(SystemTradingTableMenu.BUTTON_SET_TYPE_BUY))
                .bounds(leftX + TYPE_BUTTON_WIDTH + TYPE_BUTTON_GAP, this.topPos + TYPE_BUTTON_Y, TYPE_BUTTON_WIDTH, TYPE_BUTTON_HEIGHT)
                .build());

        Tooltip stepTooltip = Tooltip.create(Component.translatable("ui.trading_table.step.tooltip"));

        this.priceMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(SystemTradingTableMenu.BUTTON_PRICE_MINUS))
                .bounds(rightX, this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .tooltip(stepTooltip)
                .build());
        this.pricePlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(SystemTradingTableMenu.BUTTON_PRICE_PLUS))
                .bounds(rightX + 60, this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .tooltip(stepTooltip)
                .build());

        this.minMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(SystemTradingTableMenu.BUTTON_MIN_MINUS))
                .bounds(rightX, this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .tooltip(stepTooltip)
                .build());
        this.minPlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(SystemTradingTableMenu.BUTTON_MIN_PLUS))
                .bounds(rightX + 60, this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .tooltip(stepTooltip)
                .build());

        this.enableToggleButton = this.addRenderableWidget(Button.builder(resolveEnableLabel(), b -> sendButton(SystemTradingTableMenu.BUTTON_TOGGLE_ENABLED))
                .bounds(rightX, this.topPos + ENABLE_BUTTON_Y, 80, 20)
                .build());

        this.confirmTradeItemButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.trade_item.confirm"), b -> sendButton(SystemTradingTableMenu.BUTTON_CONFIRM_TRADE_ITEM))
                .bounds(leftX + 34, this.topPos + 57, 36, 20)
                .build());

        this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.save"), b -> sendSaveWithTableName())
                .bounds(rightX, this.topPos + SAVE_BUTTON_Y, 80, 20)
                .build());

        this.updateStateButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int rightX = this.leftPos + RIGHT_PANEL_X;
            if (mouseX >= rightX + 18 && mouseX <= rightX + 62 && mouseY >= this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET && mouseY <= this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET + 18) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableMenu.BUTTON_PRICE_PLUS, SystemTradingTableMenu.BUTTON_PRICE_PLUS_8, SystemTradingTableMenu.BUTTON_PRICE_PLUS_32, true);
                else this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableMenu.BUTTON_PRICE_MINUS, SystemTradingTableMenu.BUTTON_PRICE_MINUS_8, SystemTradingTableMenu.BUTTON_PRICE_MINUS_32, true);
                return true;
            }
            if (mouseX >= rightX + 18 && mouseX <= rightX + 62 && mouseY >= this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET && mouseY <= this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET + 18) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableMenu.BUTTON_MIN_PLUS, SystemTradingTableMenu.BUTTON_MIN_PLUS_8, SystemTradingTableMenu.BUTTON_MIN_PLUS_32, true);
                else this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableMenu.BUTTON_MIN_MINUS, SystemTradingTableMenu.BUTTON_MIN_MINUS_8, SystemTradingTableMenu.BUTTON_MIN_MINUS_32, true);
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
                SystemTradingTableMenu.BUTTON_MIN_PLUS, SystemTradingTableMenu.BUTTON_MIN_PLUS_8, SystemTradingTableMenu.BUTTON_MIN_PLUS_32, false)) {
            return true;
        }
        if (this.handleStepClick(this.minMinusButton, mouseX, mouseY, button,
                SystemTradingTableMenu.BUTTON_MIN_MINUS, SystemTradingTableMenu.BUTTON_MIN_MINUS_8, SystemTradingTableMenu.BUTTON_MIN_MINUS_32, false)) {
            return true;
        }
        if (this.handleStepClick(this.pricePlusButton, mouseX, mouseY, button,
                SystemTradingTableMenu.BUTTON_PRICE_PLUS, SystemTradingTableMenu.BUTTON_PRICE_PLUS_8, SystemTradingTableMenu.BUTTON_PRICE_PLUS_32, false)) {
            return true;
        }
        if (this.handleStepClick(this.priceMinusButton, mouseX, mouseY, button,
                SystemTradingTableMenu.BUTTON_PRICE_MINUS, SystemTradingTableMenu.BUTTON_PRICE_MINUS_8, SystemTradingTableMenu.BUTTON_PRICE_MINUS_32, false)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        this.sendButton(SystemTradingTableMenu.BUTTON_NAME_CLEAR);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            this.sendButton(SystemTradingTableMenu.BUTTON_NAME_APPEND_HIGH_BASE + ((ch >>> 8) & 0xFF));
            this.sendButton(SystemTradingTableMenu.BUTTON_NAME_APPEND_LOW_BASE + (ch & 0xFF));
        }
        this.sendButton(SystemTradingTableMenu.BUTTON_SAVE);
        this.savedTableNameBaseline = SystemTradingTableBlockEntity.sanitizeTableName(value);
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

    private void updateStateButtons() {
        boolean allowManage = this.menu.isAllowManage();
        if (this.typeSellButton != null) {
            this.typeSellButton.active = allowManage && this.menu.isBuyOrder();
        }
        if (this.typeBuyButton != null) {
            this.typeBuyButton.active = allowManage && !this.menu.isBuyOrder();
        }
        if (this.minPlusButton != null) {
            this.minPlusButton.active = allowManage;
        }
        if (this.minMinusButton != null) {
            this.minMinusButton.active = allowManage;
        }
        if (this.pricePlusButton != null) {
            this.pricePlusButton.active = allowManage;
        }
        if (this.priceMinusButton != null) {
            this.priceMinusButton.active = allowManage;
        }
        if (this.enableToggleButton != null) {
            this.enableToggleButton.active = allowManage;
            this.enableToggleButton.setMessage(resolveEnableLabel());
        }
        if (this.confirmTradeItemButton != null) {
            this.confirmTradeItemButton.active = allowManage && this.menu.isTradeItemSelectionDirty() && this.menu.getSlot(0).hasItem();
        }
        if (this.saveButton != null) {
            this.saveButton.active = allowManage && (this.menu.hasUnsavedManageChanges() || this.isTableNameDirty());
        }
    }

    private boolean isTableNameDirty() {
        String current = this.tableNameBox == null ? "" : SystemTradingTableBlockEntity.sanitizeTableName(this.tableNameBox.getValue());
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Component header = Component.translatable(
                "ui.trading_table.trade.header", this.title, Component.translatable("container.trading_table.manage")
        );

        int headerWidth = this.font.width(header);
        if (headerWidth > 160) {
            long time = net.minecraft.Util.getMillis();
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
            guiGraphics.enableScissor(this.leftPos + 8, this.topPos + 6, this.leftPos + 8 + 160, this.topPos + 6 + 10);
            guiGraphics.drawString(this.font, header, this.leftPos + 8 - scroll, this.topPos + 6, COLOR_TEXT, false);
            guiGraphics.disableScissor();
        } else {
            guiGraphics.drawString(this.font, header, this.leftPos + 8, this.topPos + 6, COLOR_TEXT, false);
        }

        int leftX = this.leftPos + LEFT_PANEL_X;
        int rightX = this.leftPos + RIGHT_PANEL_X;

        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.manage.name"), leftX, this.topPos + 16, COLOR_TEXT, false);
        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.manage.trade_item"), leftX, this.topPos + 48, COLOR_TEXT, false);
        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.manage.type"), leftX, this.topPos + 81, COLOR_TEXT, false);

        this.drawAdjustRow(guiGraphics, Component.translatable("ui.trading_table.manage.price"), Integer.toString(this.menu.getUnitPrice()), rightX, this.topPos + 16);
        this.drawAdjustRow(guiGraphics, Component.translatable("ui.trading_table.manage.min"), Integer.toString(this.menu.getMinTradeAmount()), rightX, this.topPos + 48);

        if (this.tableNameBox != null) {
            this.tableNameBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawAdjustRow(GuiGraphics guiGraphics, Component label, String value, int x, int y) {
        guiGraphics.drawString(this.font, label, x, y, COLOR_TEXT, false);
        Component underlined = Component.literal(value).withStyle(style -> style.withUnderlined(true));
        int valueX = x + 40 - this.font.width(underlined) / 2;
        guiGraphics.drawString(this.font, underlined, valueX, y + ROW_LABEL_OFFSET + 5, COLOR_TEXT, false);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(MANAGE_BG_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);
    }
}
