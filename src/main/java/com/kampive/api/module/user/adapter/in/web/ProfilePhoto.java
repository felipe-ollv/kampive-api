package com.kampive.api.module.user.adapter.in.web;

import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.kampive.api.module.user.domain.UserFailure;

final class ProfilePhoto {
    static String normalize(String value, String previous) {
        if(value==null || value.isBlank()) return null;
        if(value.equals(previous)) return previous;
        if(value.length()>2_800_000 || !value.matches("^data:image/(jpeg|png);base64,[A-Za-z0-9+/=]+$")) throw invalid();
        try {
            byte[] bytes=Base64.getDecoder().decode(value.substring(value.indexOf(',')+1));
            if(bytes.length>2_097_152) throw invalid();
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw invalid();
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<1 || height<1 || (long)width*height>16_000_000) throw invalid();
                    BufferedImage original=reader.read(0);
                    double scale=Math.min(1d,512d/Math.max(width,height));
                    var resized=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_RGB);
                    var graphics=resized.createGraphics();
                    try {
                        graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,resized.getWidth(),resized.getHeight());
                        graphics.drawImage(original,0,0,resized.getWidth(),resized.getHeight(),null);
                    } finally { graphics.dispose(); }
                    var output=new ByteArrayOutputStream();
                    ImageIO.write(resized,"jpeg",output);
                    return "data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(output.toByteArray());
                } finally { reader.dispose(); }
            }
        } catch(IOException | IllegalArgumentException e) { throw invalid(); }
    }
    private static UserFailure invalid() { return new UserFailure(UserFailure.Reason.INVALID_INPUT); }
}
