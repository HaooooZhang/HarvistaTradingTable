package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.menu.TradingTableInitMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class TradingTableInitScreen extends AbstractContainerScreen<TradingTableInitMenu> {
    private static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath("trading_table", "textures/gui/tradingtable_init.png");
    private static final int BG_TEXTURE_WIDTH = 256;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int COLOR_TEXT = 0x404040;
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

    public TradingTableInitScreen(TradingTableInitMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();

        int leftX = this.leftPos + LEFT_PANEL_X;
        int rightX = this.leftPos + RIGHT_PANEL_X;

        this.tableNameBox = new EditBox(this.font, leftX, this.topPos + NAME_BOX_Y + ROW_LABEL_OFFSET, NAME_BOX_WIDTH, NAME_BOX_HEIGHT, Component.translatable("ui.trading_table.init.name"));
        this.tableNameBox.setMaxLength(TradingTableBlockEntity.MAX_TABLE_NAME_LENGTH);
        this.tableNameBox.setCanLoseFocus(true);
        this.tableNameBox.setValue(this.menu.getTableName());
        this.tableNameBox.setFocused(false);
        this.addWidget(this.tableNameBox);

        this.typeSellButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.type.sell"), b -> this.handleTypeClick(false))
                .bounds(leftX, this.topPos + TYPE_BUTTON_Y, TYPE_BUTTON_WIDTH, TYPE_BUTTON_HEIGHT)
                .build());
        this.typeBuyButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.type.buy"), b -> this.handleTypeClick(true))
                .bounds(leftX + TYPE_BUTTON_WIDTH + TYPE_BUTTON_GAP, this.topPos + TYPE_BUTTON_Y, TYPE_BUTTON_WIDTH, TYPE_BUTTON_HEIGHT)
                .build());

        this.priceMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(TradingTableInitMenu.BUTTON_PRICE_MINUS))
                .bounds(rightX, this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .build());
        this.pricePlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(TradingTableInitMenu.BUTTON_PRICE_PLUS))
                .bounds(rightX + 62, this.topPos + PRICE_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .build());

        this.minMinusButton = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> sendButton(TradingTableInitMenu.BUTTON_MIN_MINUS))
                .bounds(rightX, this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .build());
        this.minPlusButton = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> sendButton(TradingTableInitMenu.BUTTON_MIN_PLUS))
                .bounds(rightX + 62, this.topPos + MIN_ROW_Y + ROW_LABEL_OFFSET, STEP_BUTTON_WIDTH, STEP_BUTTON_HEIGHT)
                .build());

        this.initializeButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.init.initialize"), b -> this.handleInitializeClick())
                .bounds(rightX, this.topPos + INITIALIZE_BUTTON_Y, 80, 18)
                .build());

        this.updateStateButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.tableNameBox != null && this.tableNameBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.tableNameBox);
            return true;
        }
        if (this.handleStepClick(this.minPlusButton, mouseX, mouseY, button,
                TradingTableInitMenu.BUTTON_MIN_PLUS, TradingTableInitMenu.BUTTON_MIN_PLUS_8, TradingTableInitMenu.BUTTON_MIN_PLUS_32)) {
            return true;
        }
        if (this.handleStepClick(this.minMinusButton, mouseX, mouseY, button,
                TradingTableInitMenu.BUTTON_MIN_MINUS, TradingTableInitMenu.BUTTON_MIN_MINUS_8, TradingTableInitMenu.BUTTON_MIN_MINUS_32)) {
            return true;
        }
        if (this.handleStepClick(this.pricePlusButton, mouseX, mouseY, button,
                TradingTableInitMenu.BUTTON_PRICE_PLUS, TradingTableInitMenu.BUTTON_PRICE_PLUS_8, TradingTableInitMenu.BUTTON_PRICE_PLUS_32)) {
            return true;
        }
        if (this.handleStepClick(this.priceMinusButton, mouseX, mouseY, button,
                TradingTableInitMenu.BUTTON_PRICE_MINUS, TradingTableInitMenu.BUTTON_PRICE_MINUS_8, TradingTableInitMenu.BUTTON_PRICE_MINUS_32)) {
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

    private void sendTableName() {
        if (this.tableNameBox == null) {
            return;
        }

        String value = this.tableNameBox.getValue();
        this.sendButton(TradingTableInitMenu.BUTTON_NAME_CLEAR);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            this.sendButton(TradingTableInitMenu.BUTTON_NAME_APPEND_HIGH_BASE + ((ch >>> 8) & 0xFF));
            this.sendButton(TradingTableInitMenu.BUTTON_NAME_APPEND_LOW_BASE + (ch & 0xFF));
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
        this.sendButton(TradingTableInitMenu.BUTTON_INITIALIZE);
    }

    private void handleTypeClick(boolean buyOrderTarget) {
        if (this.menu.isInitialized() || this.menu.isBuyOrder() == buyOrderTarget) {
            return;
        }

        this.playStepButtonSound();
        this.sendButton(TradingTableInitMenu.BUTTON_TOGGLE_TYPE);
    }

    private boolean handleStepClick(Button buttonWidget, double mouseX, double mouseY, int mouseButton,
                                    int oneId, int eightId, int thirtyTwoId) {
        if (mouseButton != 0 || buttonWidget == null || !buttonWidget.isMouseOver(mouseX, mouseY)) {
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
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int leftX = this.leftPos + LEFT_PANEL_X;
        int rightX = this.leftPos + RIGHT_PANEL_X;

        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.init.name"), leftX, this.topPos + 16, COLOR_TEXT, false);
        guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.init.trade_item"), leftX, this.topPos + 48, COLOR_TEXT, false);
        //guiGraphics.drawString(this.font, Component.translatable("ui.trading_table.init.type"), leftX, this.topPos + 75, COLOR_TEXT, false);

        this.drawAdjustRow(guiGraphics, Component.translatable("ui.trading_table.init.price"), this.menu.getUnitPrice(), rightX, this.topPos + 16);
        this.drawAdjustRow(guiGraphics, Component.translatable("ui.trading_table.init.min"), this.menu.getMinTradeAmount(), rightX, this.topPos + 48);


        if (this.tableNameBox != null) {
            this.tableNameBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawAdjustRow(GuiGraphics guiGraphics, Component label, int value, int x, int y) {
        guiGraphics.drawString(this.font, label, x, y, COLOR_TEXT, false);
        Component underlined = Component.literal(Integer.toString(value)).withStyle(style -> style.withUnderlined(true));
        int valueX = x + 40 - this.font.width(underlined) / 2;
        guiGraphics.drawString(this.font, underlined, valueX, y + ROW_LABEL_OFFSET + 4, COLOR_TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BG_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);
    }
}





