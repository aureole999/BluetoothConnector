#!/bin/bash
SQUARE_MASTER="/Users/ykuang/.gemini/antigravity/brain/5114057d-d971-418c-8ea9-b958f0088ef2/icon_render_view_1768667141726.png"
ROUND_MASTER="/Users/ykuang/.gemini/antigravity/brain/5114057d-d971-418c-8ea9-b958f0088ef2/icon_render_snapshot_1768667249924.png"
RES_DIR="/Users/ykuang/IdeaProjects/BluetoothConnector/app/src/main/res"
BRAIN_DIR="/Users/ykuang/.gemini/antigravity/brain/5114057d-d971-418c-8ea9-b958f0088ef2"

cd "$BRAIN_DIR"

# Crop Masters to 512x512
sips -c 512 512 "$SQUARE_MASTER" --out master_square_cropped.png
sips -c 512 512 "$ROUND_MASTER" --out master_round_cropped.png

# Function to process
process_icon() {
  size=$1
  folder=$2
  
  echo "Processing $folder ($size)"
  sips -z $size $size master_square_cropped.png --out "$RES_DIR/$folder/ic_launcher.png"
  sips -z $size $size master_round_cropped.png --out "$RES_DIR/$folder/ic_launcher_round.png"
}

process_icon 48 "mipmap-mdpi"
process_icon 72 "mipmap-hdpi"
process_icon 96 "mipmap-xhdpi"
process_icon 144 "mipmap-xxhdpi"
process_icon 192 "mipmap-xxxhdpi"

# Also clean up anydpi just in case (though I restored it earlier, the user wanted PNGs regenerated... 
# wait, PNGs are for legacy. anydpi is for Adaptive. I should LEAVE anydpi alone as I restored it in previous step.)
