package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.blockentity.SystemTradingTableBlockEntity;
import ink.myumoon.tradingtable.menu.SystemTradingTableInitMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.components.Tooltip;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class SystemTradingTableInitScreen extends AbstractContainerScreen<SystemTradingTableInitMenu> {
    private static final Identifier BG_TEXTURE = Identifier.fromNamespaceAndPath("trading_table", "textures/gui/systemtable_init.png");
    private static final int BG_TEXTURE_WIDTH = 256;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int LEFT_PANEL_X = 8;
    private static final int RIGHT_PANEL_X = 88;
    private static final int NAME_BOX_Y = 16;
    private static final int TYPE_BUTTON_Y = 84;
    private static final int PRICE_ROW_Y = 16;
    private static final int MIN_ROW_Y = 48;
    private static final int INITIALIZE_BUTTON_Y = 84;
    private static final int ROW_LABEL_OFFSET = 10;
    private static final int STEP_BUTTON_WIDTH = 18;
    private static final int STEP_BUTTON_HEIGHT = 18;
    private static final int TYPE_BUTTON_WIDTH = 36;
    private static final int TYPE_BUTTON_HEIGHT = 18;
    private static final int TYPE_BUTTON_GAP = 4;
    private static final int NAME_BOX_WIDTH = 76;
    private static final int NAME_BOX_HEIGHT = 18;

    private Button typeSellButton;
    private Button typeBuyButton;
    private Button minPlusButton;
    private Button pricePlusButton;
    private Button minMinusButton;
    private Button priceMinusButton;
    private Button initializeButton;
    private EditBox tableNameBox;
    private long headerScrollTime;

    public SystemTradingTableInitScreen(SystemTradingTableInitMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 190);
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();

        int leftX = this.leftPos + LEFT_PANEL_X;
        int rightX = this.leftPos + RIGHT_PANEL_X;

        this.tableNameBox = new EditBox(getFont(), leftX, this.topPos + NAME_BOX_Y + ROW_LABEL_OFFSET, NAME_BOX_WIDTH, NAME_BOX_HEIGHT, Component.translatable("ui.trading_table.init.name"));
        this.tableNameBox.setMaxLength(SystemTradingTableBlockEntity.MAX_TABLE_NAME_LENGTH);
        this.tableNameBox.setCanLoseFocus(true);
        this.tableNameBox.setValue(this.menu.getTableName());
        this.tableNameBox.setFocused(false);
        this.addRenderableWidget(this.tableNameBox);

        this.typeSellButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.type.sell"), b -> this.handleTypeClick(false))
                .bounds(leftX, this.topPos + TYPE_BUTTON_Y, TYPE_BUTTON_WIDTH, TYPE_BUTTON_HEIGHT)
                .build());
        this.typeBuyButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.type.buy"), b -> this.handleTypeClick(true))
                .bounds(leftX + TYPE_BUTTON_WIDTH + TYPE_BUTTON_GAP, this.topPos + TYPE_BUTTON_Y, TYPE_BUTTON_WIDTH, TYPE_BUTTON_HEIGHT)
                .build());

        Tooltip stepTooltip = Tooltip.create(Component.translatable("ui.trading_table.step.tooltip"));

        this.priceMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(SystemTradingTableInitMenu.BUTTON_PRICE_MINUS))
                .bounds(rightX, this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .tooltip(stepTooltip)
                .build());
        this.pricePlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(SystemTradingTableInitMenu.BUTTON_PRICE_PLUS))
                .bounds(rightX + 62, this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .tooltip(stepTooltip)
                .build());

        this.minMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(SystemTradingTableInitMenu.BUTTON_MIN_MINUS))
                .bounds(rightX, this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .tooltip(stepTooltip)
                .build());
        this.minPlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(SystemTradingTableInitMenu.BUTTON_MIN_PLUS))
                .bounds(rightX + 62, this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .tooltip(stepTooltip)
                .build());

        this.initializeButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.init.initialize"), b -> this.handleInitializeClick())
                .bounds(rightX, this.topPos + INITIALIZE_BUTTON_Y, 80, 18)
                .build());

        this.updateStateButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int rightX = this.leftPos + RIGHT_PANEL_X;
            int mods = (this.minecraft != null && this.minecraft.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                     | (this.minecraft != null && this.minecraft.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0);
            if (this.isHoveringNumber(rightX, this.topPos + PRICE_ROW_Y, mouseX, mouseY)) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableInitMenu.BUTTON_PRICE_PLUS, SystemTradingTableInitMenu.BUTTON_PRICE_PLUS_8, SystemTradingTableInitMenu.BUTTON_PRICE_PLUS_32, true, mods);
                else this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableInitMenu.BUTTON_PRICE_MINUS, SystemTradingTableInitMenu.BUTTON_PRICE_MINUS_8, SystemTradingTableInitMenu.BUTTON_PRICE_MINUS_32, true, mods);
                return true;
            }
            if (this.isHoveringNumber(rightX, this.topPos + MIN_ROW_Y, mouseX, mouseY)) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableInitMenu.BUTTON_MIN_PLUS, SystemTradingTableInitMenu.BUTTON_MIN_PLUS_8, SystemTradingTableInitMenu.BUTTON_MIN_PLUS_32, true, mods);
                else this.handleStepClick(null, mouseX, mouseY, 0, SystemTradingTableInitMenu.BUTTON_MIN_MINUS, SystemTradingTableInitMenu.BUTTON_MIN_MINUS_8, SystemTradingTableInitMenu.BUTTON_MIN_MINUS_32, true, mods);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isHoveringNumber(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x + 18 && mouseX <= x + 62 && mouseY >= y + ROW_LABEL_OFFSET && mouseY <= y + ROW_LABEL_OFFSET + 18;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        int mods = event.modifiers();
        if (this.tableNameBox != null && this.tableNameBox.mouseClicked(event, flag)) {
            this.setFocused(this.tableNameBox);
            return true;
        }
        if (this.handleStepClick(this.minPlusButton, mouseX, mouseY, button,
                SystemTradingTableInitMenu.BUTTON_MIN_PLUS, SystemTradingTableInitMenu.BUTTON_MIN_PLUS_8, SystemTradingTableInitMenu.BUTTON_MIN_PLUS_32, false, mods)) {
            return true;
        }
        if (this.handleStepClick(this.minMinusButton, mouseX, mouseY, button,
                SystemTradingTableInitMenu.BUTTON_MIN_MINUS, SystemTradingTableInitMenu.BUTTON_MIN_MINUS_8, SystemTradingTableInitMenu.BUTTON_MIN_MINUS_32, false, mods)) {
            return true;
        }
        if (this.handleStepClick(this.pricePlusButton, mouseX, mouseY, button,
                SystemTradingTableInitMenu.BUTTON_PRICE_PLUS, SystemTradingTableInitMenu.BUTTON_PRICE_PLUS_8, SystemTradingTableInitMenu.BUTTON_PRICE_PLUS_32, false, mods
        )) {
            return true;
        }
        if (this.handleStepClick(this.priceMinusButton, mouseX, mouseY, button,
                SystemTradingTableInitMenu.BUTTON_PRICE_MINUS, SystemTradingTableInitMenu.BUTTON_PRICE_MINUS_8, SystemTradingTableInitMenu.BUTTON_PRICE_MINUS_32, false, mods)) {
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
        if (this.menu.isInitialized()) {
            this.onClose();
            return;
        }
        this.updateStateButtons();
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void sendTableName() {
        if (this.tableNameBox == null) {
            return;
        }

        String value = this.tableNameBox.getValue();
        this.sendButton(SystemTradingTableInitMenu.BUTTON_NAME_CLEAR);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            this.sendButton(SystemTradingTableInitMenu.BUTTON_NAME_APPEND_HIGH_BASE + ((ch >>> 8) & 0xFF));
            this.sendButton(SystemTradingTableInitMenu.BUTTON_NAME_APPEND_LOW_BASE + (ch & 0xFF));
        }
    }

    private void handleInitializeClick() {
        if (this.menu.isInitialized()) {
            return;
        }
        if (this.menu.getConfiguredTradeItem().isEmpty()) {
            return;
        }
        this.sendTableName();
        this.sendButton(SystemTradingTableInitMenu.BUTTON_INITIALIZE);
    }

    private void handleTypeClick(boolean buyOrderTarget) {
        if (this.menu.isInitialized() || this.menu.isBuyOrder() == buyOrderTarget) {
            return;
        }

        this.playStepButtonSound();
        this.sendButton(SystemTradingTableInitMenu.BUTTON_TOGGLE_TYPE);
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
        boolean initialized = this.menu.isInitialized();
        if (this.typeSellButton != null) {
            this.typeSellButton.active = !initialized && this.menu.isBuyOrder();
        }
        if (this.typeBuyButton != null) {
            this.typeBuyButton.active = !initialized && !this.menu.isBuyOrder();
        }
        if (this.minPlusButton != null) {
            this.minPlusButton.active = !initialized;
        }
        if (this.minMinusButton != null) {
            this.minMinusButton.active = !initialized;
        }
        if (this.pricePlusButton != null) {
            this.pricePlusButton.active = !initialized;
        }
        if (this.priceMinusButton != null) {
            this.priceMinusButton.active = !initialized;
        }
        if (this.initializeButton != null) {
            this.initializeButton.active = !initialized && !this.menu.getConfiguredTradeItem().isEmpty();
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        doTick();
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawAdjustRow(GuiGraphicsExtractor guiGraphics, Component label, int value, int x, int y) {
        guiGraphics.text(getFont(), label, x, y, COLOR_TEXT, false);
        Component underlined = Component.literal(Integer.toString(value)).withStyle(style -> style.withUnderlined(true));
        int valueX = x + 40 - getFont().width(underlined) / 2;
        guiGraphics.text(getFont(), underlined, valueX, y + ROW_LABEL_OFFSET + 4, COLOR_TEXT, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BG_TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);

        Component header = Component.translatable(
                "ui.trading_table.trade.header", this.title, Component.translatable("container.trading_table.init")
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
            guiGraphics.enableScissor(this.leftPos + 8, this.topPos + 6, this.leftPos + 8 + 160, this.topPos + 6 + 10);
            guiGraphics.text(getFont(), header, this.leftPos + 8 - scroll, this.topPos + 6, COLOR_TEXT, false);
            guiGraphics.disableScissor();
        } else {
            guiGraphics.text(getFont(), header, this.leftPos + 8, this.topPos + 6, COLOR_TEXT, false);
        }

        int leftX = this.leftPos + LEFT_PANEL_X;
        int rightX = this.leftPos + RIGHT_PANEL_X;

        guiGraphics.text(getFont(), Component.translatable("ui.trading_table.init.name"), leftX, this.topPos + 16, COLOR_TEXT, false);
        guiGraphics.text(getFont(), Component.translatable("ui.trading_table.init.trade_item"), leftX, this.topPos + 48, COLOR_TEXT, false);

        this.drawAdjustRow(guiGraphics, Component.translatable("ui.trading_table.init.price"), this.menu.getUnitPrice(), rightX, this.topPos + 16);
        this.drawAdjustRow(guiGraphics, Component.translatable("ui.trading_table.init.min"), this.menu.getMinTradeAmount(), rightX, this.topPos + 48);
    }
}
