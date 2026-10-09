// Standalone adapter: externally supplied DirPlayer PFR1 parser, no player runtime.
mod pfr {
    pub mod bit_reader { include!(concat!(env!("PFR_REFERENCE_DIR"), "/bit_reader.rs")); }
    pub mod types { include!(concat!(env!("PFR_REFERENCE_DIR"), "/types.rs")); }
    pub mod header { include!(concat!(env!("PFR_REFERENCE_DIR"), "/header.rs")); }
    pub mod physical { include!(concat!(env!("PFR_REFERENCE_DIR"), "/physical.rs")); }
    pub mod stroke_builder { include!(concat!(env!("PFR_REFERENCE_DIR"), "/stroke_builder.rs")); }
    pub mod glyph { include!(concat!(env!("PFR_REFERENCE_DIR"), "/glyph.rs")); }
    fn log(_: &str) {}
}
use std::io::Write;
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 3 { return Err("usage: reference input.pfr output.json".into()); }
    let data = std::fs::read(&args[1])?;
    let header = pfr::header::parse_pfr_header(&data)?;
    let end = (header.phys_font_section_offset + header.phys_font_section_size) as usize;
    let mut physical = pfr::physical::parse_physical_font(&data, header.phys_font_section_offset as usize,
        end.min(header.gps_section_offset as usize), header.max_chars)?;
    physical.max_x_orus = header.max_x_orus;
    physical.max_y_orus = header.max_y_orus;
    pfr::physical::initialize_stroke_tables_fallback(&mut physical);
    let logical = pfr::header::parse_logical_font_directory(&data, &header)?;
    let matrix = logical.first().map(|l| l.font_matrix).unwrap_or([256,0,0,256]);
    let base = header.gps_section_offset as usize;
    let mut known: Vec<usize> = physical.char_records.iter().map(|r| r.gps_offset as usize).collect();
    known.sort_unstable();
    known.dedup();
    let mut output = std::fs::OpenOptions::new().write(true).create_new(true).open(&args[2])?;
    write!(output, "{{\"units\":{},\"metric_units\":{},\"ascender\":{},\"descender\":{},\"glyphs\":[",
        physical.outline_resolution,physical.metrics_resolution,physical.y_max,physical.y_min)?;
    for (index, record) in physical.char_records.iter().enumerate() {
        if index>0 { write!(output,",")?; }
        let start = base + record.gps_offset as usize;
        let size = record.gps_size as usize;
        let contours = if size<=1 {Vec::new()} else {
            let mut parser = pfr::glyph::Pfr1HeaderParser::new(&data[start..start+size], &matrix,
                physical.outline_resolution, header.max_x_orus,header.max_y_orus,
                physical.metrics.std_vw as f32,physical.metrics.std_hw as f32,
                &data,base as i32,header.gps_section_size as usize,record.gps_offset as i32,
                Some(&known),Some(&physical),Some(&physical.metrics),0);
            parser.parse().contours
        };
        write!(output,"{{\"code\":{},\"width\":{},\"gps_size\":{},\"contours\":[",
            record.char_code,record.set_width,size)?;
        for (ci,contour) in contours.iter().enumerate() {
            if ci>0 {write!(output,",")?;} write!(output,"[")?;
            let mut first = true;
            for command in &contour.commands {
                let kind = match command.cmd_type {
                    pfr::types::PfrCmdType::MoveTo=>0,pfr::types::PfrCmdType::LineTo=>1,
                    pfr::types::PfrCmdType::CurveTo=>2,pfr::types::PfrCmdType::Close=>continue,
                };
                if !first {write!(output,",")?;} first=false;
                write!(output,"[{},{},{},{},{},{},{}]",kind,command.x,command.y,
                    command.x1,command.y1,command.x2,command.y2)?;
            }
            write!(output,"]")?;
        }
        write!(output,"]}}")?;
    }
    write!(output,"]}}")?;
    println!("reference records={}", physical.char_records.len());
    Ok(())
}
