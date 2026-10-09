"""Render private reader contours beside the FreeType-rendered CFF result."""
import argparse
import json
from pathlib import Path
from matplotlib.backends.backend_agg import FigureCanvasAgg
from matplotlib.figure import Figure
from matplotlib.path import Path as ContourPath
from matplotlib.patches import PathPatch
from PIL import Image, ImageDraw, ImageFont


def outline_text(axis, data, text, size, x, baseline):
    records = {g['code']:g for g in data['glyphs']}
    scale = size/data['units']
    for character in text:
        record=records[ord(character)]
        vertices, codes = [], []
        def point(xx, yy): return (x+xx*scale, baseline-yy*scale)
        for contour in record['contours']:
            for kind,xx,yy,x1,y1,x2,y2 in contour:
                if kind==0:
                    vertices.append(point(xx,yy)); codes.append(ContourPath.MOVETO)
                elif kind==1:
                    vertices.append(point(xx,yy)); codes.append(ContourPath.LINETO)
                else:
                    vertices.extend([point(x1,y1),point(x2,y2),point(xx,yy)])
                    codes.extend([ContourPath.CURVE4]*3)
            vertices.append((0,0)); codes.append(ContourPath.CLOSEPOLY)
        if vertices:
            axis.add_patch(PathPatch(ContourPath(vertices,codes),facecolor='#20284b',edgecolor='none'))
        x+=record['width']*size/data['metric_units']


def render(before, reference, corrected, font, output, title):
    output=Path(output)
    if output.exists(): raise ValueError('output exists')
    texts=['C a e g 9', 'Agente: Dream', 'Caso 2: Dinero F\u00e1cil', '\u00c1\u00c9\u00cd\u00d3\u00da \u00e1\u00e9\u00ed\u00f3\u00fa', '\u00d1\u00f1\u00fc \u00a1\u00bf 0123456789']
    labels=['LibreShockwave anterior','PFR1: lector Rust','LibreShockwave corregido','FontTools CFF / FreeType']
    image=Image.new('RGB',(2240,540),'#f4ecda'); draw=ImageDraw.Draw(image)
    label=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22)
    draw.text((20,14),title,fill='black',font=label)
    for col,path in enumerate([before,reference,corrected]):
        data=json.loads(Path(path).read_text())
        figure=Figure(figsize=(5.6,5.4),dpi=100)
        canvas=FigureCanvasAgg(figure)
        figure.patch.set_alpha(0)
        axis=figure.add_axes([0,0,1,1]);axis.set_xlim(0,560);axis.set_ylim(540,0);axis.axis('off')
        for row,text in enumerate(texts): outline_text(axis,data,text,40,16,145+row*76)
        canvas.draw()
        layer=Image.frombuffer('RGBA',(560,540),canvas.buffer_rgba(),'raw','RGBA',0,1)
        image.paste(layer,(col*560,0),layer)
    draw=ImageDraw.Draw(image)
    face=ImageFont.truetype(str(font),40)
    for col,labeltext in enumerate(labels):
        draw.text((col*560+16,70),labeltext,fill='black',font=label)
        for row,text in enumerate(texts):
            baseline=145+row*76
            draw.line((col*560+8,baseline,(col+1)*560-8,baseline),fill='#c59790')
            if col==3: draw.text((col*560+16,baseline),text,font=face,fill='#20284b',anchor='ls')
    draw.text((16,515),'Sin hinting original. Rust aplica punto fijo en compuestos; diferencia < 1/1000 em.',fill='black',font=label)
    image.save(output)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['before','reference','corrected','font','output']:p.add_argument(name)
    p.add_argument('--title',required=True);a=p.parse_args()
    render(a.before,a.reference,a.corrected,a.font,a.output,a.title)
