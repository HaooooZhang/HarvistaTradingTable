package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.blockentity.SystemTradingTableBlockEntity;
import ink.myumoon.tradingtable.menu.SystemTradingTableMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class SystemTradingTableScreen extends AbstractContainerScreen<SystemTradingTableMenu> {
    private static final Identifier MANAGE_BG_TEXTURE = Identifier.fromNamespaceAndPath("trading_table", "textures/gui/systemtable_manger.png");
    private static final int BG_TEXTURE_WIDTH = 256;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int COLOR_TEXT = 0xFF404040;
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
        super(menu, playerInventory, title, 176, 210);
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();

        int leftX = this.leftPos + LEFT_PANEL_X;
        int rightX = this.leftPos + RIGHT_PANEL_X;

        this.tableNameBox = new EditBox(getFont(), leftX, this.topPos + NAME_BOX_Y + ROW_LABEL_OFFSET, NAME_BOX_WIDTH, NAME_BOX_HEIGHT, Component.translatable("ui.trading_table.manage.name"));
        this.tableNameBox.setMaxLength(SystemTradingTableBlockEntity.MAX_TABLE_NAME_LENGTH);
        this.tableNameBox.setCanLoseFocus(true);
        this.savedTableNameBaseline = SystemTradingTableBlockEntity.sanitizeTableName(extractInitialTableName());
        this.tableNameBox.setValue(this.savedTableNameBaseline);
        this.tableNameBox.setFocused(false);
        this.addRenderableWidget(this.tableNameBox);

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
            int mods = (this.minecraft != null && this.minecraft.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                     | (this.minecraft != null && this.minecraft.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0);
            if (mouseX >= rightX + 18 && mouseX <= rightX + 62 && mouseY >= this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET && mouseY <= this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET + 18) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableMenu.BUTTON_PRICE_PLUS, SystemTradingTableMenu.BUTTON_PRICE_PLUS_8, SystemTradingTableMenu.BUTTON_PRICE_PLUS_32, true, mods);
                else this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableMenu.BUTTON_PRICE_MINUS, SystemTradingTableMenu.BUTTON_PRICE_MINUS_8, SystemTradingTableMenu.BUTTON_PRICE_MINUS_32, true, mods);
                return true;
            }
            if (mouseX >= rightX + 18 && mouseX <= rightX + 62 && mouseY >= this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET && mouseY <= this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET + 18) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableMenu.BUTTON_MIN_PLUS, SystemTradingTableMenu.BUTTON_MIN_PLUS_8, SystemTradingTableMenu.BUTTON_MIN_PLUS_32, true, mods);
                else this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableMenu.BUTTON_MIN_MINUS, SystemTradingTableMenu.BUTTON_MIN_MINUS_8, SystemTradingTableMenu.BUTTON_MIN_MINUS_32, true, mods);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        int mods = event.modifiers();
        if (this.tableNameBox != null && this.tableNameBox.mouseClicked(event, flag)) {
            this.setFocused(this.tableNameBox);
            return true;
        }
        if (this.handleStepClick(this.minPlusButton, mx, my, button,
                SystemTradingTableMenu.BUTTON_MIN_PLUS, SystemTradingTableMenu.BUTTON_MIN_PLUS_8, SystemTradingTableMenu.BUTTON_MIN_PLUS_32, false, mods)) {
            return true;
        }
        if (this.handleStepClick(this.minMinusButton, mx, my, button,
                SystemTradingTableMenu.BUTTON_MIN_MINUS, SystemTradingTableMenu.BUTTON_MIN_MINUS_8, SystemTradingTableMenu.BUTTON_MIN_MINUS_32, false, mods)) {
            return true;
        }
        if (this.handleStepClick(this.pricePlusButton, mx, my, button,
                SystemTradingTableMenu.BUTTON_PRICE_PLUS, SystemTradingTableMenu.BUTTON_PRICE_PLUS_8, SystemTradingTableMenu.BUTTON_PRICE_PLUS_32, false, mods)) {
            return true;
        }
        if (this.handleStepClick(this.priceMinusButton, mx, my, button,
                SystemTradingTableMenu.BUTTON_PRICE_MINUS, SystemTradingTableMenu.BUTTON_PRICE_MINUS_8, SystemTradingTableMenu.BUTTON_PRICE_MINUS_32, false, mods)) {
            return true;
        }
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.tableNameBox != null && this.tableNameBox.isFocused() && this.tableNameBox.keyPressed(event)) {
            return true;
        }
        if (this.tableNameBox != null && this.tableNameBox.isFocused() && this.minecraft != null
                && this.minecraft.options.keyInventory.matches(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.tableNameBox != null && this.tableNameBox.isFocused() && this.tableNameBox.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    // tick() in 26.1.2 is final, moved to extractRenderState
    private void doTick() {
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
                                    int oneId, int eightId, int thirtyTwoId, boolean bypassButtonCheck, int modifiers) {
        if (!bypassButtonCheck && (mouseButton != 0 || buttonWidget == null || !buttonWidget.isMouseOver(mouseX, mouseY))) {
            return false;
        }

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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        doTick();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawAdjustRow(GuiGraphicsExtractor g, Component label, String value, int x, int y) {
        g.text(getFont(), label, x, y, COLOR_TEXT, false);
        Component underlined = Component.literal(value).withStyle(style -> style.withUnderlined(true));
        int valueX = x + 40 - getFont().width(underlined) / 2;
        g.text(getFont(), underlined, valueX, y + ROW_LABEL_OFFSET + 5, COLOR_TEXT, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, MANAGE_BG_TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);
        super.extractContents(g, mouseX, mouseY, partialTick);

        Component header = Component.translatable(
                "ui.trading_table.trade.header", this.title, Component.translatable("container.trading_table.manage")
        );

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
            g.enableScissor(this.leftPos + 8, this.topPos + 6, this.leftPos + 8 + 160, this.topPos + 6 + 10);
            g.text(getFont(), header, this.leftPos + 8 - scroll, this.topPos + 6, COLOR_TEXT, false);
            g.disableScissor();
        } else {
            g.text(getFont(), header, this.leftPos + 8, this.topPos + 6, COLOR_TEXT, false);
        }

        int leftX = this.leftPos + LEFT_PANEL_X;
        int rightX = this.leftPos + RIGHT_PANEL_X;

        g.text(getFont(), Component.translatable("ui.trading_table.manage.name"), leftX, this.topPos + 16, COLOR_TEXT, false);
        g.text(getFont(), Component.translatable("ui.trading_table.manage.trade_item"), leftX, this.topPos + 48, COLOR_TEXT, false);
        g.text(getFont(), Component.translatable("ui.trading_table.manage.type"), leftX, this.topPos + 81, COLOR_TEXT, false);

        this.drawAdjustRow(g, Component.translatable("ui.trading_table.manage.price"), Integer.toString(this.menu.getUnitPrice()), rightX, this.topPos + 16);
        this.drawAdjustRow(g, Component.translatable("ui.trading_table.manage.min"), Integer.toString(this.menu.getMinTradeAmount()), rightX, this.topPos + 48);
    }
}
