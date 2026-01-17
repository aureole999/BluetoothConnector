from PIL import Image
import sys

def get_dominant_color(image_path):
    img = Image.open(image_path)
    img = img.convert('RGB')
    # Get the color of the middle pixel or average
    # Since ic_launcher_background is usually a solid color or gradient
    # let's just get the pixel at (width/2, height/2)
    width, height = img.size
    return img.getpixel((width // 2, height // 2))

if __name__ == "__main__":
    path = "/Users/ykuang/IdeaProjects/BluetoothConnector/app/src/main/res/mipmap-xxhdpi/ic_launcher_background.png"
    r, g, b = get_dominant_color(path)
    print(f"#{r:02x}{g:02x}{b:02x}")
