package ink.myumoon.tradingtable.client.screen;

import ink.myumoon.tradingtable.config.Config;
import ink.myumoon.tradingtable.economy.NeoEssentialsEconomyBackend;
import ink.myumoon.tradingtable.blockentity.TradingTableBlockEntity;
import ink.myumoon.tradingtable.menu.TradingTableMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class TradingTableScreen extends AbstractContainerScreen<TradingTableMenu> {
    private static final Identifier MANAGE_BG_TEXTURE = Identifier.fromNamespaceAndPath("trading_table", "textures/gui/tradingtable_manger.png");
    private static final int BG_TEXTURE_WIDTH = 512;
    private static final int BG_TEXTURE_HEIGHT = 256;
    private static final int COLOR_TEXT = 0xFF404040;
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
        super(menu, playerInventory, title, 336, 166);
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
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
        this.tableNameBox = new EditBox(getFont(), rightX, this.topPos + 26, 76, 18, Component.translatable("ui.trading_table.manage.name"));
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
        if (!Config.isNeoEssentialsMode()) {
            this.extractButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.extract"), b -> sendButton(TradingTableMenu.BUTTON_EXTRACT))
                    .bounds(rightX, this.topPos + 106, 76, 20)
                    .build());
        }
        if (!Config.isNeoEssentialsMode()) {
            this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.save"), b -> sendSaveWithTableName())
                .bounds(rightX, this.topPos + 130, 76, 20)
                .build());
        }else{
            this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.trading_table.manage.save"), b -> sendSaveWithTableName())
                .bounds(rightX, this.topPos + 108, 76, 20)
                .build());
        }
        this.updateStateButtons();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int leftX = this.leftPos + PANEL_LEFT_X;
            int topY = this.topPos + 10;
            int mods = (this.minecraft != null && this.minecraft.hasControlDown() ? GLFW.GLFW_MOD_CONTROL : 0)
                     | (this.minecraft != null && this.minecraft.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0);
            if (mouseX >= leftX + 20 && mouseX <= leftX + 56 && mouseY >= topY + 50 && mouseY <= topY + 70) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, TradingTableMenu.BUTTON_PRICE_PLUS, TradingTableMenu.BUTTON_PRICE_PLUS_8, TradingTableMenu.BUTTON_PRICE_PLUS_32, true, mods);
                else this.handleStepClick(null, mouseX, mouseY, 0, TradingTableMenu.BUTTON_PRICE_MINUS, TradingTableMenu.BUTTON_PRICE_MINUS_8, TradingTableMenu.BUTTON_PRICE_MINUS_32, true, mods);
                return true;
            }
            if (mouseX >= leftX + 20 && mouseX <= leftX + 56 && mouseY >= topY + 84 && mouseY <= topY + 104) {
                if (scrollY > 0) this.handleStepClick(null, mouseX, mouseY, 0, TradingTableMenu.BUTTON_MIN_PLUS, TradingTableMenu.BUTTON_MIN_PLUS_8, TradingTableMenu.BUTTON_MIN_PLUS_32, true, mods);
                else this.handleStepClick(null, mouseX, mouseY, 0, TradingTableMenu.BUTTON_MIN_MINUS, TradingTableMenu.BUTTON_MIN_MINUS_8, TradingTableMenu.BUTTON_MIN_MINUS_32, true, mods);
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
                TradingTableMenu.BUTTON_MIN_PLUS, TradingTableMenu.BUTTON_MIN_PLUS_8, TradingTableMenu.BUTTON_MIN_PLUS_32, false, mods)) {
            return true;
        }
        if (this.handleStepClick(this.minMinusButton, mx, my, button,
                TradingTableMenu.BUTTON_MIN_MINUS, TradingTableMenu.BUTTON_MIN_MINUS_8, TradingTableMenu.BUTTON_MIN_MINUS_32, false, mods)) {
            return true;
        }
        if (this.handleStepClick(this.pricePlusButton, mx, my, button,
                TradingTableMenu.BUTTON_PRICE_PLUS, TradingTableMenu.BUTTON_PRICE_PLUS_8, TradingTableMenu.BUTTON_PRICE_PLUS_32, false, mods)) {
            return true;
        }
        if (this.handleStepClick(this.priceMinusButton, mx, my, button,
                TradingTableMenu.BUTTON_PRICE_MINUS, TradingTableMenu.BUTTON_PRICE_MINUS_8, TradingTableMenu.BUTTON_PRICE_MINUS_32, false, mods)) {
            return true;
        }
        if (this.handleExtractClick(mx, my, button, mods)) {
            return true;
        }
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(event, dragX, dragY);
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

    // tick() 在 26.1.2 是 final，更新逻辑移到 extractRenderState

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


    private boolean handleExtractClick(double mouseX, double mouseY, int mouseButton, int modifiers) {
        if (mouseButton != 0 || this.extractButton == null || !this.extractButton.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (this.minecraft != null && this.minecraft.hasControlDown()) {
            this.playStepButtonSound();
            this.sendButton(TradingTableMenu.BUTTON_EXTRACT_ALL);
            return true;
        }
        if (this.minecraft != null && this.minecraft.hasShiftDown()) {
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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        this.updateStateButtons();

        Component header = Component.translatable(
                "ui.trading_table.trade.header",
                Component.literal(this.tableNameBox == null || this.tableNameBox.getValue().isBlank() ? this.title.getString() : this.tableNameBox.getValue()),
                Component.translatable("container.trading_table.manage")
        );

        int headerWidth = getFont().width(header);
        if (headerWidth > 160) {
            long time = System.currentTimeMillis();
            if (this.headerScrollTime == 0) {
                this.headerScrollTime = time;
            }
            long delta = time - this.headerScrollTime;
            long pauseDuration = 1800;

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
            g.enableScissor(this.leftPos + 88, this.topPos + 6, this.leftPos + 88 + 160, this.topPos + 6 + 10);
            g.text(getFont(), header, this.leftPos + 88 - scroll, this.topPos + 6, COLOR_TEXT, false);
            g.disableScissor();
        } else {
            g.text(getFont(), header, this.leftPos + 88, this.topPos + 6, COLOR_TEXT, false);
        }

        int leftX = this.leftPos + PANEL_LEFT_X;
        g.text(getFont(), Component.translatable("ui.trading_table.manage.type"), leftX, this.topPos + 16, COLOR_TEXT, false);
        this.drawAdjustBlock(g, Component.translatable("ui.trading_table.manage.price"), Integer.toString(this.menu.getUnitPrice()), leftX, this.topPos + 50);
        this.drawAdjustBlock(g, Component.translatable("ui.trading_table.manage.min"), Integer.toString(this.menu.getMinTradeAmount()), leftX, this.topPos + 84);

        g.text(getFont(), this.playerInventoryTitle, this.leftPos + 88, this.topPos + 73, COLOR_TEXT, false);

        int rightX = this.leftPos + PANEL_RIGHT_X;
        g.text(getFont(), Component.translatable("ui.trading_table.manage.name"), rightX, this.topPos + 16, COLOR_TEXT, false);
        g.text(getFont(), Component.translatable("ui.trading_table.manage.trade_item"), rightX, this.topPos + 48, COLOR_TEXT, false);

        g.text(getFont(), Component.translatable("ui.trading_table.manage.currency"), rightX, this.topPos + 82, COLOR_TEXT, false);
        String balance = String.format(Locale.ROOT, "%.1f", this.menu.getCashierBalance());
        g.text(getFont(), balance, rightX + 8, this.topPos + 94, COLOR_TEXT, false);
        if (Config.isNeoEssentialsMode()) {
            String symbol = NeoEssentialsEconomyBackend.getCurrencySymbol();
            g.text(getFont(), symbol, rightX + getFont().width(balance) + 2 + 8, this.topPos + 94, COLOR_TEXT, false);
        } else {
            Item currencyItem = Config.getCurrencyItem();
            g.item(new ItemStack(currencyItem), rightX + getFont().width(balance) + 2 + 8, this.topPos + 90);
        }

        if (this.tableNameBox != null) {
            this.tableNameBox.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
        }
    }

    private void drawAdjustBlock(GuiGraphicsExtractor g, Component label, String value, int x, int y) {
        g.text(getFont(), label, x, y, COLOR_TEXT, false);
        Component underlined = Component.literal(value).withStyle(style -> style.withUnderlined(true));
        int centerX = x + 42 - 4;
        g.text(getFont(), underlined, centerX - getFont().width(underlined) / 2, y + 16, COLOR_TEXT, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, MANAGE_BG_TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, BG_TEXTURE_WIDTH, BG_TEXTURE_HEIGHT);
        super.extractContents(g, mouseX, mouseY, partialTick);
    }
}

