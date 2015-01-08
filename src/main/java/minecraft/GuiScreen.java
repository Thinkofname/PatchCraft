package minecraft;

public interface GuiScreen {

    void addButton(int id, int x, int y, int width, int height, String text);

    int getScreenWidth();

    int getScreenHeight();
}
