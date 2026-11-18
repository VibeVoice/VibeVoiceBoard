import sys
import xml.etree.ElementTree as ET
A='{http://schemas.android.com/apk/res/android}'
src,out=sys.argv[1],sys.argv[2]
root=ET.parse(src).getroot()
vw=root.get(A+'viewportWidth'); vh=root.get(A+'viewportHeight')
def node(n):
    s=''
    for c in n:
        tag=c.tag.split('}')[-1]
        if tag=='group':
            tx=c.get(A+'translateX','0'); ty=c.get(A+'translateY','0')
            sx=c.get(A+'scaleX','1'); sy=c.get(A+'scaleY','1')
            r=c.get(A+'rotation','0'); px=c.get(A+'pivotX','0'); py=c.get(A+'pivotY','0')
            t=f'translate({tx} {ty})'
            if float(r)!=0: t+=f' rotate({r} {px} {py})'
            if float(sx)!=1 or float(sy)!=1: t+=f' translate({px} {py}) scale({sx} {sy}) translate(-{px} -{py})'
            s+=f'<g transform="{t}">'+node(c)+'</g>'
        elif tag=='path':
            fill=c.get(A+'fillColor','#000')
            fr=c.get(A+'fillType')
            rule=' fill-rule="evenodd"' if fr=='evenOdd' else ''
            s+=f'<path fill="{fill}"{rule} d="{c.get(A+"pathData")}"/>'
    return s
open(out,'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}">{node(root)}</svg>\n')
