/*
  Copyright 2006 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package sim.portrayal;
import sim.portrayal.inspector.*;
import java.awt.*;
import sim.engine.*;
import sim.util.gui.*;
import sim.util.*;
import sim.display.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

/**
   A simple inspector class that looks at the "getX" and "setX" method of the object to be investigates
   and creates a user-friendly graphical interface with read only and read/write components of the object.
   
   <p>SimpleInspector automatically creates an UpdateButton and adds it to itself at position BorderLayout.NORTH
   whenever you set it to be non-volatile, and when you set it to be volatile, it removes the UpdateButton.
   
   <p>SimpleInspector automatically sets the title of the inspetor to the object name.
*/

public class SimpleInspector extends Inspector
    {
    public static final int DEFAULT_MAX_PROPERTIES = 100;
    int maxProperties = DEFAULT_MAX_PROPERTIES;
    /** The GUIState  of the simulation */
    GUIState state;
    /** The property list displayed -- this may change at any time */
    LabelledList propertyList;
    /** The generated object properties -- this may change at any time */
    Properties properties;
    /** Each of the property fields in the property list, not all of which may exist at any time. */
    PropertyField[] members = new PropertyField[0];
    /** The current index of the topmost element */
    int start = 0;
    /** The number of items presently in the propertyList */
    int count = 0;
    JPanel header = new JPanel()
        {
        public Insets getInsets () { return new Insets(2,2,2,2); }
        };
    String listName;  // used internally
    JLabel numElements = null;
    Box startField = null;
    
    public GUIState getGUIState() { return state; }
    public int getMaxProperties() { return maxProperties; }
    
    /** Creates a new SimpleInspector with the given properties, state, maximum number of properties, and
        "name".  The name is what's shown in the labelled list of the SimpleInspector.  It is not the
        title of the SimpleInspector (what appears in a window).  For that, use setTitle. */
    public SimpleInspector(Properties properties, GUIState state, String name, int maxProperties)
        {
        numElements = new JLabel();
        this.maxProperties = maxProperties;
        setLayout(new BorderLayout());
        this.state = state;
        listName = name;
        header.setLayout(new BorderLayout());
        add(header,BorderLayout.NORTH);
        this.properties = properties;
        generateProperties(0);
        setTitle("" + properties.getObject());
        }
        
    /** Creates a new SimpleInspector with the given properties, state, and
        "name".  The name is what's shown in the labelled list of the SimpleInspector.  It is not the
        title of the SimpleInspector (what appears in a window).  For that, use setTitle. */
    public SimpleInspector(Properties properties, GUIState state, String name)
        {
        this(properties, state, name, state.getMaximumPropertiesForInspector());
        }
        
    /** Creates a new SimpleInspector with the given properties and state. */
    public SimpleInspector(Object object, GUIState state)
        {
        this(object,state,null);
        }
        
    /** Creates a new SimpleInspector with the given object, state, and
        "name".  The name is what's shown in the labelled list of the SimpleInspector.  It is not the
        title of the SimpleInspector (what appears in a window).  For that, use setTitle. */
    public SimpleInspector(Object object, GUIState state, String name) 
        {
        this(object, state, name, state.getMaximumPropertiesForInspector());
        }
        
    /** Creates a new SimpleInspector with the given object, state, maximum number of properties, and
        "name".  The name is what's shown in the labelled list of the SimpleInspector.  It is not the
        title of the SimpleInspector (what appears in a window).  For that, use setTitle. */
    public SimpleInspector(Object object, GUIState state, String name, int maxProperties) 
        {
        this(Properties.getProperties(object), state, name, maxProperties);
        }
    
    /* Creates a JPopupMenu that possibly includes "View" to
       view the object instead of using the ViewButton.  If not, returns null. */
    JPopupMenu makePreliminaryPopup(final int index)
        {
        if (properties.isComposite(index))
            {
            JPopupMenu popup = new JPopupMenu();
            JMenuItem menu = new JMenuItem("View");
            menu.setEnabled(true);
            menu.addActionListener(new ActionListener()
                {
                public void actionPerformed(ActionEvent e)
                    {
                    Properties props = properties;
                    final Inspector inspector = Inspector.getInspector(props.getValue(index), SimpleInspector.this.state, null);
                    Stoppable stopper = null;
                    try 
                        {
                        stopper = SimpleInspector.this.state.scheduleRepeatingImmediatelyAfter(inspector.getUpdateSteppable());
                        }
                    catch (java.lang.IllegalArgumentException ee)  // this can happen if the simulation is over, so nothing further can be scheduled (notably the Stopper)
                        {
                        // make a dummy stopper
                        stopper = new Stoppable() { public void stop() { } };
                        }
                    stopper = inspector.reviseStopper(stopper);
                    SimpleInspector.this.state.controller.registerInspector(inspector,stopper);
                    JFrame frame = inspector.createFrame(stopper);
                    frame.setVisible(true);
                    }
                });
            popup.add(menu);
            return popup;
            }
        else return null;
        }
    
    PropertyField makePropertyField(final int index)
        {
        Class type = properties.getType(index);
        final Properties props = properties;            // see UNUSUAL BUG note below
        return new PropertyField(
            null,
            properties.betterToString(properties.getValue(index)),
            properties.isReadWrite(index),
            properties.getDomain(index),
                (properties.isComposite(index) ?
                //PropertyField.SHOW_VIEWBUTTON : 
                PropertyField.SHOW_TEXTFIELD :
                    (type == Boolean.TYPE || type == Boolean.class ?
                    PropertyField.SHOW_CHECKBOX :
                        (properties.getDomain(index) == null ? PropertyField.SHOW_TEXTFIELD :
                        (properties.getDomain(index) instanceof Interval) ? 
                        PropertyField.SHOW_SLIDER : PropertyField.SHOW_LIST ))))
            {
            // The return value should be the value you want the display to show instead.
            public String newValue(final String newValue)
                {
                // UNUSUAL BUG: if I say this:
                // Properties props = properties;
                // ...or...
                // Properties props = SimpleInspector.this.properties
                // ... then sometimes props is set to null even though clearly
                // properties is non-null above, since it'd be impossible to return a
                // PropertyField otherwise.  So instead of declaring it as an instance
                // variable here, we declare it as a final closure variable above.
                                
                // the underlying model could still be running, so we need
                // to do this safely
                synchronized(SimpleInspector.this.state.state.schedule)
                    {
                    // try to set the value
                    if (props.setValue(index, newValue) == null)
                        java.awt.Toolkit.getDefaultToolkit().beep();
                    // refresh the controller -- if it exists yet
                    if (SimpleInspector.this.state.controller != null)
                        SimpleInspector.this.state.controller.refresh();
                    // set text to the new value
                    return props.betterToString(props.getValue(index));
                    }
                }
            };
        }
    
    /** Private method.  Does a repaint that is guaranteed to work (on some systems, plain repaint())
        fails if there's lots of updates going on as is the case in our simulator thread.  */
    void doEnsuredRepaint(final Component component)
        {
        SwingUtilities.invokeLater(new Runnable()
            {
            public void run()
                {
                if (component!=null) component.repaint();
                }
            });
        }

    void generateProperties(int start)
        {
        final int len = properties.numProperties();
        if (start < 0) start = 0;
        if (start > len) return;  // failed
                
        if (propertyList != null) 
            remove(propertyList);
        propertyList = new LabelledList(listName);
        if (len > maxProperties)
            {
            final String s = "Page forward/back through properties.  " + maxProperties + " properties shown at a time.";
            if (startField == null)
                {
                NumberTextField f = new NumberTextField(" Go to ", start,1,maxProperties)
                    {
                    public double newValue(double newValue)
                        {
                        int newIndex = (int) newValue;
                        if (newIndex<0) newIndex = 0;
                        if (newIndex >= len) return (int)getValue();
                        // at this point we need to build a new properties list!
                        generateProperties(newIndex);
                        return newIndex; // for good measure, though it'll be gone by now
                        }
                    };

                f.setToolTipText(s);
                numElements.setText(" of " + len + " ");
                numElements.setToolTipText(s);
                f.getField().setColumns(4);
                startField = new Box(BoxLayout.X_AXIS);
                startField.add(f);
                startField.add(numElements);
                startField.add(Box.createGlue());
                header.add(startField, BorderLayout.CENTER);
                }
            }
        else 
            {
            start = 0;
            if (startField!=null) header.remove(startField);
            }

        members = new PropertyField[len];

        int end = start + maxProperties;
        if (end > len) end = len;
        count = end - start;

        //hack: sort the names.
        Vector<String> v = new Vector<String>();
        Vector<JLabel> labels = new Vector<JLabel>();
        Vector<JToggleButton> toggles = new Vector<JToggleButton>();
        Vector<Boolean> isHiddens = new Vector<Boolean>();
        Vector<PropertyField> membersList = new Vector<PropertyField>();
        Vector<String> descriptions = new Vector<String>();
        //step 1: loop through it once, store (buffer) everything
        for( int i = start ; i < end; i++ ){
            //System.out.println(properties.getName(i));
            v.add(properties.getName(i));
            labels.add(new JLabel(properties.getName(i) + " "));
            toggles.add(PropertyInspector.getPopupMenu(properties,i,state, makePreliminaryPopup(i)));
            isHiddens.add(properties.isHidden(i));
            membersList.add(makePropertyField(i));
            descriptions.add(properties.getDescription(i));
        }
        //list construction
        //System.out.println(v); //before
        Vector<String> vPreSort = new Vector<String>(v); //stores the before with a copy constructor
        Collections.sort(v,String.CASE_INSENSITIVE_ORDER); //sort
        //debug: check sorted lists.
        //System.out.println(v);
        //System.out.println(vPreSort);

        //step 2: go through the normal loop with the sorted index reference, restore the values
        //and then feed the data back as expected.
        for( int i = start ; i < end; i++ ){
            String curName = v.get(i);
            //System.out.println(i+", "+curName); //debug: print current loop number and item
            int curIndex = vPreSort.indexOf(curName);
            //System.out.println("at "+curIndex); //debug: print the target index at the buffer.
            if(!isHiddens.get(curIndex)){
                JLabel label = labels.get(curIndex);
                JToggleButton toggle = toggles.get(curIndex);
                members[i] = membersList.get(curIndex);
                propertyList.add(null,
                    label, 
                    toggle, 
                    members[i], 
                    null);                
                // load tooltips
                String description = descriptions.get(curIndex);
                if (description != null)
                    {
                    if (label != null) label.setToolTipText(description);
                    if (toggle != null) toggle.setToolTipText(description);    // do we want this one?
                    if (members[i] != null) members[i].setToolTipText(description);  // do we want this one?
                    }
            }
            else members[i] = null; 
        }
        // original code:
        // for( int i = start ; i < end; i++ )
        //     {
        //     if (!properties.isHidden(i))  // don't show if the user asked that it be hidden
        //         {
        //         JLabel label = new JLabel(properties.getName(i) + " ");
        //         JToggleButton toggle = PropertyInspector.getPopupMenu(properties,i,state, makePreliminaryPopup(i));
        //         members[i] = makePropertyField(i);
        //         propertyList.add(null,
        //             label, 
        //             toggle, 
        //             members[i], 
        //             null);
                
        //         // load tooltips
        //         String description = properties.getDescription(i);
        //         if (description != null)
        //             {
        //             if (label != null) label.setToolTipText(description);
        //             if (toggle != null) toggle.setToolTipText(description);    // do we want this one?
        //             if (members[i] != null) members[i].setToolTipText(description);  // do we want this one?
        //             }
        //         }
        //     else members[i] = null;
        //     }
        //end hack
        add(propertyList, BorderLayout.CENTER);
        this.start = start;
        revalidate();
        }
    
    JButton updateButton = null;
    public void setVolatile(boolean val)
        {
        super.setVolatile(val);
        if (isVolatile())
            {
            if (updateButton!=null) 
                {
                header.remove(updateButton); revalidate();
                }
            }
        else
            {
            if (updateButton==null)
                {
                updateButton = (JButton) makeUpdateButton();
                                
                // modify height -- stupid MacOS X 1.4.2 bug has icon buttons too big
                NumberTextField sacrificial = new NumberTextField(1,true);
                Dimension d = sacrificial.getPreferredSize();
                d.width = updateButton.getPreferredSize().width;                                
                updateButton.setPreferredSize(d);
                d = sacrificial.getMinimumSize();
                d.width = updateButton.getMinimumSize().width;
                updateButton.setMinimumSize(d);
                                
                // add to header
                header.add(updateButton,BorderLayout.WEST);
                revalidate(); 
                }
            } 
        }

    public void updateInspector()
        {
        if (properties.isVolatile())  // need to rebuild each time, YUCK
            {
            remove(propertyList);
            generateProperties(start);
            doEnsuredRepaint(this);
            }
        else for( int i = start ; i < start+count ; i++ )
                 if (members[i] != null) 
                     members[i].setValue(properties.betterToString(properties.getValue(i)));
        }
    }
