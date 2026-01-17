import os
from PIL import Image, ImageDraw

def process_icons():
    source_path = "/Users/ykuang/.gemini/antigravity/brain/5114057d-d971-418c-8ea9-b958f0088ef2/icon_render_view_1768667141726.png"
    project_res = "/Users/ykuang/IdeaProjects/BluetoothConnector/app/src/main/res"
    
    # Load and crop
    img = Image.open(source_path)
    # Crop top-left 512x512
    icon_square = img.crop((0, 0, 512, 512))
    
    # Create round version
    icon_round = icon_square.copy()
    mask = Image.new('L', (512, 512), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, 512, 512), fill=255)
    icon_round.putalpha(mask)
    
    sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }
    
    for folder, size in sizes.items():
        out_dir = os.path.join(project_res, folder)
        if not os.path.exists(out_dir):
            os.makedirs(out_dir)
            
        # Resize and save square
        resized_square = icon_square.resize((size, size), Image.Resampling.LANCZOS)
        resized_square.save(os.path.join(out_dir, "ic_launcher.png"))
        
        # Resize and save round
        resized_round = icon_round.resize((size, size), Image.Resampling.LANCZOS)
        resized_round.save(os.path.join(out_dir, "ic_launcher_round.png"))
        
    print("Icon processing complete.")

if __name__ == "__main__":
    process_icons()
