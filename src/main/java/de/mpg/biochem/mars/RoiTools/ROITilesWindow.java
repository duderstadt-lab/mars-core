/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.RoiTools;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.DirectoryChooser;
import ij.process.ImageProcessor;

public class ROITilesWindow implements MouseListener, MouseMotionListener, ActionListener {
	private String input_path;
	
	protected Thread thread;
	private FrameSkipAnimator FSanimator;
	private boolean running = false;
	
	private JButton previousButton = new JButton("previous");
	private JButton nextButton = new JButton("next");
	private JButton Process = new JButton("Process");
	private JButton play = new JButton("Start/Stop");
	private JTextField sliceInterval = new JTextField("25",3);
	private JLabel pos_label = new JLabel();
	private JLabel file_label = new JLabel();
	
	//private Button input_button;
	//private TextField input_text;
	
	private String[] filenames;
	private int panel_offset = 0;
	private int vertical_tiles = 2;
	private int horizontal_tiles = 2;
	private double Roi_lineWidth = 0.25;
	private int processed_to = 0;
	
	private ArrayList<Integer> tile_x_position = new ArrayList<Integer>();
	private ArrayList<Integer> tile_y_position = new ArrayList<Integer>();
	private ArrayList<Roi> rois = new ArrayList<Roi>();
	private ArrayList<String> loaded_names = new ArrayList<String>();
	
	private ArrayList<String> rejected_trajectories = new ArrayList<String>();
	private ArrayList<String> accepted_trajectories = new ArrayList<String>();
	private ImageWindow win;
	
	ImagePlus tiled_images;
	private int tile_height, tile_width, frame_num;
	
	public ROITilesWindow(String input_path, int vertical_tiles, int horizontal_tiles) {
		this.input_path = input_path;
		this.vertical_tiles = vertical_tiles;
		this.horizontal_tiles = horizontal_tiles;
		
		load_names();
		
		//lets also set the height and width for each panel
		//We assume all are videos have the same dimensions
		ImagePlus image = new ImagePlus(input_path + "/" + filenames[0]);
		tile_height = image.getHeight();
		tile_width = image.getWidth();
		frame_num = image.getStackSize();
		
		//build tile positions and rois
		for (int w=0; w < horizontal_tiles; w++) {
			for (int c=0; c < vertical_tiles; c++) {
				tile_x_position.add(w*tile_width);
				tile_y_position.add(c*tile_height);
				rois.add(new Roi(w*tile_width + Roi_lineWidth/2, c*tile_height + Roi_lineWidth/2, tile_width - Roi_lineWidth, tile_height - Roi_lineWidth));
			}
		}
		
		//Load first set of tiled images...
		tiled_images = new ImagePlus("Tiled Images", load_imageSet());
		tiled_images.show("Trajectory Set");
		draw_rois();
		
		tiled_images.getCanvas().addMouseListener(this);
		tiled_images.getCanvas().addMouseMotionListener(this);
		win = tiled_images.getWindow();
		win.addKeyListener(new KeyAdapter() {

	           @Override
	           public void keyPressed(KeyEvent e) {
	        	   if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
	        		   if (panel_offset + vertical_tiles*horizontal_tiles < filenames.length) {
	       					panel_offset += vertical_tiles*horizontal_tiles;
	       					tiled_images.setStack(load_imageSet());
	       					draw_rois();
	       				}
	        	   } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
	        		   if (panel_offset > 0) {
	       					panel_offset -= vertical_tiles*horizontal_tiles;
	       					tiled_images.setStack(load_imageSet());
	       					draw_rois();
	       				}
	        	   }
	           }
	       });
		
		win.setFocusable(true);    // necessary for key listener
		
		Panel panel = new Panel();
		panel.setLayout(new FlowLayout());
		panel.add(previousButton);
		panel.add(nextButton);
		panel.add(Process);
		panel.add(play);
		panel.add(new JLabel(" Slice Interval"));
		panel.add(sliceInterval);
		previousButton.addActionListener(this);
		nextButton.addActionListener(this);
		Process.addActionListener(this);
		play.addActionListener(this);
		win.add(panel);
		
		//pos_label.setMinimumSize(new Dimension(30,150));
		//pos_label.setMaximumSize(new Dimension(50,400));
		//file_label.setMinimumSize(new Dimension(30,150));
		//file_label.setMaximumSize(new Dimension(50,400));
		
		Panel labpan = new Panel();
		labpan.add(pos_label);
		labpan.add(file_label);
		win.add(labpan);
		
		for (int e=0 ; e < 8 ; e++)
			tiled_images.getCanvas().zoomIn(horizontal_tiles*tile_width/2, vertical_tiles*tile_height/2);
		
	}
	
	private boolean load_names() {
		//First we need to get all the tiff files in the input directory. 
		FilenameFilter textFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.endsWith(".tif")) {
					return true;
				} else {
					return false;
				}
			}
		};
		
		//input_path = input_text.getText();
		filenames = (new File(input_path)).list(textFilter);
		if (filenames.length == 0) {
			return false;
		} else {
			return true;
		}
	}
	
	private ImageStack load_imageSet() {
		//Load in current set of videos...
		ArrayList<ImageStack> images = new ArrayList<ImageStack>();
		loaded_names.clear();
		int to_num = panel_offset;
		for (int i=panel_offset; i < (panel_offset + vertical_tiles*horizontal_tiles); i++) {
			if (i < filenames.length) {
				images.add((new ImagePlus(input_path + "/" + filenames[i]).getStack()));
				loaded_names.add(filenames[i]);
				to_num++;
				if (processed_to < i+1) {
					processed_to++;
					if (!accepted_trajectories.contains(filenames[i])) {
						rejected_trajectories.add(filenames[i]);
					}
				}
			}
		}
		
		ImageStack tile_stack = new ImageStack(tile_width*horizontal_tiles, tile_height*vertical_tiles, images.get(0).getColorModel());
		ImageProcessor template_processor = images.get(0).getProcessor(1);
		
		//build tiled image stack
		for (int q=0; q < frame_num ; q++) {
			ImageProcessor cur_image = template_processor.createProcessor(tile_width*horizontal_tiles, tile_height*vertical_tiles);
			for (int w=0; w < horizontal_tiles*vertical_tiles; w++) {
				if (w < images.size()) {
					cur_image.insert(images.get(w).getProcessor(1),tile_x_position.get(w),tile_y_position.get(w));
					images.get(w).deleteSlice(1);
				}
			}
			tile_stack.addSlice(null, cur_image);
		}
		
		pos_label.setText("ROIs " + (panel_offset + 1) + " to " + to_num + " of " + filenames.length);
		
		return tile_stack;
	}
	
	void draw_rois() {
		Overlay over = new Overlay();
		for (int i = 0; i < loaded_names.size(); i++) {
			rois.get(i).setStrokeWidth(Roi_lineWidth);
			if (rejected_trajectories.contains(loaded_names.get(i))) {
				rois.get(i).setStrokeColor(Color.RED);
			} else {
				rois.get(i).setStrokeColor(Color.GREEN);
			}
			over.add(rois.get(i));
    	}
		tiled_images.getCanvas().setOverlay(over);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == previousButton) {
			if (panel_offset > 0) {
				panel_offset -= vertical_tiles*horizontal_tiles;
				tiled_images.setStack(load_imageSet());
				draw_rois();
			}
			win.requestFocus();
		}
		else if (e.getSource() == nextButton) {
			if (panel_offset + vertical_tiles*horizontal_tiles < filenames.length) {
				panel_offset += vertical_tiles*horizontal_tiles;
				tiled_images.setStack(load_imageSet());
				draw_rois();
			}
			win.requestFocus();
		}
		else if (e.getSource() == Process) {
			File reject_directory = new File(input_path + "/rejected");
			if(!reject_directory.exists()) {
				reject_directory.mkdir();
			}
			File accepted_directory = new File(input_path + "/accepted");
			if(!accepted_directory.exists()) {
				accepted_directory.mkdir();
			}
			for (int i=0; i < rejected_trajectories.size(); i++) {
				(new File(input_path + "/" + rejected_trajectories.get(i))).renameTo(new File(input_path + "/rejected/" + rejected_trajectories.get(i)));
			}
			rejected_trajectories.clear();
			for (int i=0; i < accepted_trajectories.size(); i++) {
				(new File(input_path + "/" + accepted_trajectories.get(i))).renameTo(new File(input_path + "/accepted/" + accepted_trajectories.get(i)));
			}
			accepted_trajectories.clear();
			processed_to = 0;
			if (load_names()) {
				panel_offset = 0;
				tiled_images.setStack(load_imageSet());
				draw_rois();
			} else {
				pos_label.setText("Done!");
				file_label.setText("");
			}
			win.requestFocus();
		} else if (e.getSource() == play) {
			if (running) {
				running = false;
				FSanimator.kill = true;
			} else {
				running = true;
				FSanimator = new FrameSkipAnimator(Integer.parseInt(sliceInterval.getText()));
				thread = new Thread(FSanimator, "skip-animator");
				thread.start();
			}
			win.requestFocus();
		}
		/*else if (e.getSource() == input_button) {
			DirectoryChooser dirChooser = new DirectoryChooser("Choose directory");
			String directory = dirChooser.getDirectory();
			
			if (directory != null) {
				input_text.setText(directory);
			}
		}*/
	}
	
	public void mouseClicked(MouseEvent e) {
    	Point pon = tiled_images.getCanvas().getCursorLoc();
    	for (int i = 0; i < loaded_names.size(); i++) {
    		if (rois.get(i).contains(pon.x, pon.y)) {
    			if (rejected_trajectories.contains(loaded_names.get(i))) {
    				rejected_trajectories.remove(loaded_names.get(i));
    				accepted_trajectories.add(loaded_names.get(i));
    			} else {
    				rejected_trajectories.add(loaded_names.get(i));
    				accepted_trajectories.remove(loaded_names.get(i));
    			}
    		}
    	}
    	draw_rois();
    }
	
    public void mouseMoved(MouseEvent e) {
    	Point pon = tiled_images.getCanvas().getCursorLoc();
    	for (int i = 0; i < loaded_names.size(); i++) {
    		if (rois.get(i).contains(pon.x, pon.y)) {
    			file_label.setText(" file: " + loaded_names.get(i));
    		}
    	}
    }
    //Don't need these..there must be a way to get rid of this stuff?!?
  	public void mouseReleased(MouseEvent e) {
    }
    public void mouseEntered(MouseEvent e) {
    }
    public void mousePressed(MouseEvent e) {
    }
    public void mouseExited(MouseEvent e) {
    }	
    public void mouseDragged(MouseEvent e) {
    }
}
